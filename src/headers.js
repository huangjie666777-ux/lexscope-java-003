import { ParseError } from './errors.js';

// Existing whole-message field reader. The decoder also uses it for trailers.
export function parseFields(lines) {
  return lines.map(line => {
    const colon = line.indexOf(':');
    if (colon < 1) throw new ParseError('SYNTAX', 'Missing field name or colon');
    return [line.slice(0, colon).trim().toLowerCase(), line.slice(colon + 1).trim()];
  });
}

export function readHead(text) {
  const [requestLine, ...lines] = text.split('\r\n');
  const [method, target, version] = requestLine.trim().split(/\s+/);
  if (!method || !target || version !== 'HTTP/1.1') {
    throw new ParseError('SYNTAX', 'Invalid request line');
  }
  const headers = parseFields(lines);
  // The current implementation normalizes fields for lookup.
  const lookup = Object.fromEntries(headers);
  if (!lookup.host) throw new ParseError('SYNTAX', 'Host is required');
  return { method, target, version, headers, lookup };
}

export function framing(lookup) {
  if (lookup['transfer-encoding']) {
    if (!lookup['transfer-encoding'].toLowerCase().includes('chunked')) {
      throw new ParseError('SYNTAX', 'Unsupported transfer coding');
    }
    return { kind: 'chunked' };
  }
  const length = Number.parseInt(lookup['content-length'] || '0', 10);
  if (!Number.isFinite(length) || length < 0) {
    throw new ParseError('SYNTAX', 'Invalid content length');
  }
  return { kind: 'fixed', length };
}
