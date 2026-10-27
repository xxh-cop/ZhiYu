export type DataStateKind = 'empty' | 'error' | 'loading' | 'ready';

export interface DataStateInput {
  error?: string;
  isEmpty: boolean;
  loading: boolean;
}

export function resolveDataState({
  error,
  isEmpty,
  loading,
}: DataStateInput): DataStateKind {
  if (error) {
    return 'error';
  }
  if (loading) {
    return 'loading';
  }
  if (isEmpty) {
    return 'empty';
  }
  return 'ready';
}
