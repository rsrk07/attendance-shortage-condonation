import { useCallback, useEffect, useRef, useState } from "react";

/**
 * Loads data from the API and optionally keeps it fresh by polling.
 * Polling pauses while the browser tab is hidden.
 */
export function useApi(loader, deps = [], { interval = 0, active = true } = {}) {
  const [data, setData] = useState(null);
  const [error, setError] = useState(null);
  const [loading, setLoading] = useState(true);
  const [refreshedAt, setRefreshedAt] = useState(null);
  const loaderRef = useRef(loader);
  loaderRef.current = loader;

  const reload = useCallback(async () => {
    try {
      const result = await loaderRef.current();
      setData(result);
      setError(null);
      setRefreshedAt(new Date());
    } catch (e) {
      setError(e);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    setLoading(true);
    setData(null);
    reload();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, deps);

  useEffect(() => {
    if (!interval || !active) return undefined;
    const id = setInterval(() => {
      if (!document.hidden) reload();
    }, interval);
    return () => clearInterval(id);
  }, [interval, active, reload]);

  return { data, error, loading, reload, refreshedAt };
}
