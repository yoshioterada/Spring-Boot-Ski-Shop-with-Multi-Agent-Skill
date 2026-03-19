export function generateTraceId(): string {
  return crypto.randomUUID().replace(/-/g, '');
}

export function generateSpanId(): string {
  return crypto.randomUUID().replace(/-/g, '').substring(0, 16);
}

export function createTraceparent(): string {
  const version = '00';
  const traceId = generateTraceId();
  const spanId = generateSpanId();
  const flags = '01'; // sampled
  return `${version}-${traceId}-${spanId}-${flags}`;
}
