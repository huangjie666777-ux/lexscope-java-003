import { ParseError } from './errors.js';
import { readHead, framing } from './headers.js';
import { decodeChunks } from './chunks.js';

export class RequestDecoder {
  constructor({ maxHeaderBytes = 8192, maxBodyBytes = 1048576 } = {}) {
    this.maxHeaderBytes = maxHeaderBytes;
    this.maxBodyBytes = maxBodyBytes;
    this.pending = '';
    this.closed = false;
  }

  push(buffer) {
    if (this.closed) throw new ParseError('CLOSED', 'Decoder is closed');
    if (!Buffer.isBuffer(buffer)) throw new TypeError('Expected Buffer');
    this.pending += buffer.toString('utf8');
    const completed = [];
    while (this.pending.length) {
      const headEnd = this.pending.indexOf('\r\n\r\n');
      if (headEnd < 0) return completed;
      if (headEnd + 4 > this.maxHeaderBytes) throw new ParseError('LIMIT', 'Header limit exceeded');
      const head = readHead(this.pending.slice(0, headEnd));
      const mode = framing(head.lookup);
      const bodyStart = headEnd + 4;
      let body, trailers, consumed;
      if (mode.kind === 'fixed') {
        if (this.pending.length < bodyStart + mode.length) return completed;
        if (mode.length > this.maxBodyBytes) throw new ParseError('LIMIT', 'Body limit exceeded');
        body = Buffer.from(this.pending.slice(bodyStart, bodyStart + mode.length));
        trailers = [];
        consumed = bodyStart + mode.length;
      } else {
        const chunked = decodeChunks(this.pending.slice(bodyStart), this.maxBodyBytes);
        if (chunked === null) return completed;
        ({ body, trailers } = chunked);
        consumed = bodyStart + chunked.consumed;
      }
      completed.push({ method: head.method, target: head.target, version: head.version,
        headers: head.headers, body, trailers });
      this.pending = this.pending.slice(consumed);
    }
    return completed;
  }

  end() {
    this.closed = true;
    if (this.pending.length) throw new ParseError('TRUNCATED', 'Unexpected EOF');
    return [];
  }
}
