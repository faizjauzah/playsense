export type Sort<K extends string> = { key: K; descending: boolean } | null;

/** Rows with no value stay last in both directions, so a price sort never opens on a wall of dashes. */
export function sortRows<T extends { name: string }>(
  rows: T[],
  valueOf: (row: T) => string | number | null,
  descending: boolean,
): T[] {
  return [...rows].sort((a, b) => {
    const left = valueOf(a);
    const right = valueOf(b);
    if (left === null && right === null) return a.name.localeCompare(b.name);
    if (left === null) return 1;
    if (right === null) return -1;
    const order =
      typeof left === "string" && typeof right === "string"
        ? left.localeCompare(right)
        : Number(left) - Number(right);
    if (order === 0) return a.name.localeCompare(b.name);
    return descending ? -order : order;
  });
}

export function nextSort<K extends string>(current: Sort<K>, key: K, firstDescending: boolean): Sort<K> {
  if (current?.key === key) return { key, descending: !current.descending };
  return { key, descending: firstDescending };
}
