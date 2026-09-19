import { ParseError } from './errors.js';
import { parseFields } from './headers.js';

// Returns null while waiting for more data, otherwise consumes one chunked body.
export function decodeChunks(text, maxBodyBytes) {
  const pieces = [];
  let offset = 0;
  let total = 0;
  while (true) {
    const lineEnd = text.indexOf('\r\n', offset);
    if (lineEnd < 0) return null;
    const size = Number.parseInt(text.slice(offset, lineEnd), 16);
    if (!Number.isFinite(size) || size < 0) {
      throw new ParseError('SYNTAX', 'Invalid chunk size');
    }
    offset = lineEnd + 2;
    if (size === 0) {
      if (text.slice(offset, offset + 2) === '\r\n') {
        return { body: Buffer.from(pieces.join('')), trailers: [], consumed: offset + 2 };
      }
      const end = text.indexOf('\r\n\r\n', offset);
      if (end < 0) return null;
      const trailers = parseFields(text.slice(offset, end).split('\r\n'));
      return { body: Buffer.from(pieces.join('')), trailers, consumed: end + 4 };
    }
    if (text.length < offset + size + 2) return null;
    pieces.push(text.slice(offset, offset + size));
    total += size;
    if (total > maxBodyBytes) throw new ParseError('LIMIT', 'Body limit exceeded');
    offset += size + 2;
  }
}
