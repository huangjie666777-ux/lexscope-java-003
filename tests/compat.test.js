import test from 'node:test';
import assert from 'node:assert/strict';
import { RequestDecoder, ParseError } from '../src/index.js';

test('simple request returns the public message shape', () => {
  const d = new RequestDecoder();
  const [m] = d.push(Buffer.from('GET /health HTTP/1.1\r\nHost: local\r\nX-Tag: one\r\n\r\n'));
  assert.deepEqual(m, { method: 'GET', target: '/health', version: 'HTTP/1.1',
    headers: [['host', 'local'], ['x-tag', 'one']], body: Buffer.alloc(0), trailers: [] });
  assert.deepEqual(d.end(), []);
});

test('ASCII body can span input calls', () => {
  const d = new RequestDecoder();
  assert.deepEqual(d.push(Buffer.from('POST / HTTP/1.1\r\nHost: local\r\nContent-Length: 4\r\n\r\nab')), []);
  assert.equal(d.push(Buffer.from('cd'))[0].body.toString(), 'abcd');
});

test('pipelined empty messages keep their order', () => {
  const d = new RequestDecoder();
  const m = d.push(Buffer.from('GET /a HTTP/1.1\r\nHost: local\r\n\r\nGET /b HTTP/1.1\r\nHost: local\r\n\r\n'));
  assert.deepEqual(m.map(x => x.target), ['/a', '/b']);
});

test('basic ASCII chunked body', () => {
  const d = new RequestDecoder();
  const [m] = d.push(Buffer.from('POST / HTTP/1.1\r\nHost: local\r\nTransfer-Encoding: chunked\r\n\r\n2\r\nab\r\n0\r\n\r\n'));
  assert.equal(m.body.toString(), 'ab');
  assert.deepEqual(m.trailers, []);
});

test('ParseError exposes code and completed messages', () => {
  const messages = [];
  const e = new ParseError('SYNTAX', 'bad', messages);
  assert.ok(e instanceof Error);
  assert.equal(e.name, 'ParseError');
  assert.equal(e.code, 'SYNTAX');
  assert.equal(e.completed, messages);
});
