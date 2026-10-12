import { useCallback, useEffect, useRef, useState } from "react";

/** Keep pending requests and retry keys scoped to one viewer/video. */
export function useVideoComments({ videoId, token, open, request, onCount, onUnauthorized }) {
  const [items, setItems] = useState([]);
  const [state, setState] = useState("idle");
  const [error, setError] = useState("");
  const [failedAction, setFailedAction] = useState(null);
  const [text, setText] = useState("");
  const [cursor, setCursor] = useState("");
  const [hasMore, setHasMore] = useState(false);
  const [busy, setBusy] = useState(false);
  const [deleting, setDeleting] = useState(null);
  const scope = `${token || ""}:${videoId || ""}`;
  const context = useRef(scope);
  context.current = scope;
  const alive = useRef(true);
  const loadVersion = useRef(0);
  const mutation = useRef(null);
  const retry = useRef(null);
  const callbacks = useRef({ onCount, onUnauthorized });
  callbacks.current = { onCount, onUnauthorized };

  useEffect(() => {
    alive.current = true;
    return () => { alive.current = false; loadVersion.current++; };
  }, []);

  const load = useCallback(async (position = "") => {
    if (!videoId) return;
    const version = ++loadVersion.current;
    setState(position ? "loadingMore" : "loading");
    setError("");
    setFailedAction(null);
    try {
      const params = new URLSearchParams({ limit: "20" });
      if (position) params.set("cursor", position);
      const data = await request(`/api/videos/${videoId}/comments?${params}`);
      if (!alive.current || context.current !== scope || version !== loadVersion.current) return;
      setItems((previous) => {
        const merged = position ? [...previous, ...(data.items || [])] : data.items || [];
        return [...new Map(merged.map((item) => [item.id, item])).values()];
      });
      setCursor(data.next_cursor || "");
      setHasMore(Boolean(data.has_more));
      setState("ready");
    } catch (err) {
      if (!alive.current || context.current !== scope || version !== loadVersion.current) return;
      setFailedAction({ type: "load", position });
      setError(err.message || "评论加载失败");
      setState("error");
    }
  }, [videoId, scope, request]);

  useEffect(() => {
    loadVersion.current++;
    mutation.current = null;
    retry.current = null;
    setItems([]);
    setText("");
    setCursor("");
    setHasMore(false);
    setError("");
    setState("idle");
    setBusy(false);
    setDeleting(null);
    setFailedAction(null);
  }, [scope]);

  useEffect(() => {
    if (open) load();
    return () => { loadVersion.current++; };
  }, [open, load]);

  async function submit() {
    const content = text.trim();
    if (!token || !videoId || !content || mutation.current) return;
    if (Array.from(content).length > 1000) {
      setError("评论最多 1000 个字符");
      return;
    }
    if (!retry.current || retry.current.content !== content || retry.current.scope !== scope) {
      retry.current = { content, scope, key: `web-comment-${crypto.randomUUID()}` };
    }
    const operation = {};
    mutation.current = operation;
    setBusy(true);
    setError("");
    setFailedAction(null);
    try {
      const data = await request(`/api/videos/${videoId}/comments`, {
        method: "POST", token, headers: { "Idempotency-Key": retry.current.key }, body: { content }
      });
      if (!alive.current || context.current !== scope || mutation.current !== operation) return;
      loadVersion.current++;
      setItems((previous) => [data, ...previous.filter((item) => item.id !== data.id)]);
      setText("");
      retry.current = null;
      callbacks.current.onCount(videoId, data.comment_count);
      setState("ready");
      await load();
    } catch (err) {
      if (!alive.current || context.current !== scope || mutation.current !== operation) return;
      if (err.status === 401) callbacks.current.onUnauthorized();
      setFailedAction({ type: "submit" });
      setError(err.message || "评论发布失败，请重试");
    } finally {
      if (alive.current && context.current === scope && mutation.current === operation) {
        mutation.current = null;
        setBusy(false);
      }
    }
  }

  async function remove(id) {
    if (!token || mutation.current) return;
    const operation = {};
    mutation.current = operation;
    setDeleting(id);
    setError("");
    setFailedAction(null);
    try {
      const data = await request(`/api/comments/${id}`, { method: "DELETE", token });
      if (!alive.current || context.current !== scope || mutation.current !== operation) return;
      loadVersion.current++;
      setItems((previous) => previous.filter((item) => item.id !== id));
      callbacks.current.onCount(videoId, data.comment_count);
      setState("ready");
      await load();
    } catch (err) {
      if (!alive.current || context.current !== scope || mutation.current !== operation) return;
      if (err.status === 401) callbacks.current.onUnauthorized();
      setFailedAction({ type: "delete", id });
      setError(err.message || "删除评论失败，请重试");
    } finally {
      if (alive.current && context.current === scope && mutation.current === operation) {
        mutation.current = null;
        setDeleting(null);
      }
    }
  }

  return { items, state, error, text, setText, hasMore, busy, deleting, submit, remove,
    reload: () => load(), loadMore: () => load(cursor),
    retry: () => {
      if (busy || deleting !== null) return;
      if (failedAction?.type === "submit") return submit();
      if (failedAction?.type === "delete") return remove(failedAction.id);
      return load(failedAction?.position || "");
    } };
}
