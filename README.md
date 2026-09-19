# httpframe

An existing, dependency-free decoder for captured HTTP request bytes. It is an
offline parser, not an HTTP server. The modules currently handle common ASCII
examples, but incremental framing, validation and failure state need repair.
The rules below are the target contract; passing the compatibility tests alone
does not establish correctness. Node.js 22.14.0 is already available.

## Public API

Import `RequestDecoder` and `ParseError` from `src/index.js` (ES modules).
`new RequestDecoder({maxHeaderBytes=8192, maxBodyBytes=1048576}={})` accepts valid
integer options: maxHeaderBytes >= 1 and maxBodyBytes >= 0. No option validation
is required. `push(buffer)` receives only Node Buffers and synchronously returns
an array of all newly completed messages. Empty Buffers are valid. No callbacks,
streams or sockets are required. `end()` reports EOF and returns an empty array
if currently at a request boundary, including a completely empty stream.

Each message has exactly these fields:

```js
{
  method: 'POST', target: '/upload', version: 'HTTP/1.1',
  headers: [['host', 'local'], ['content-length', '2']],
  body: Buffer.from([0, 255]), trailers: []
}
```

Fields are ordered `[lowercaseName, trimmedValue]` pairs; repeated ordinary
fields remain separate and in wire order. Trim only ASCII SP and HTAB around
values. Do not merge trailers into headers. Bodies contain the decoded payload
bytes, not chunk framing. Input buffers may be reused/mutated by the caller after
push returns; accepted bytes and returned bodies must remain unchanged. Returned
body buffers must not alias input buffers. No object-freezing requirement.

## Exact supported wire subset

- Request line: `METHOD SP TARGET SP HTTP/1.1 CRLF`, exactly one ASCII space at
  each separator. METHOD is a nonempty HTTP token. TARGET starts with `/` and
  otherwise consists of visible ASCII bytes 0x21..0x7e excluding `#`. No URL
  decoding, absolute-form, HTTP/1.0, response parsing, upgrade or CONNECT handling.
- Token characters: ASCII letters, digits and `!#$%&'*+-.^_` followed by the
  backtick character, vertical bar and tilde. This same set defines field names.
- All request/header/trailer/chunk-size line endings are CRLF. Reject bare LF,
  CR followed by a non-LF byte, leading blank lines, obsolete folding, empty field
  names, and whitespace before a field colon. A final CR split across pushes is
  incomplete until the next byte or EOF. A field value contains only HTAB or
  ASCII 0x20..0x7e (no other controls, DEL or non-ASCII bytes).
- Exactly one Host field with a nonempty trimmed value is required. No host-name
  or port grammar validation. Other ordinary fields, including empty values,
  are allowed and preserved; duplicate ordinary fields are allowed.
- Content-Length (CL) and Transfer-Encoding (TE) names are case-insensitive.
  CL+TE together, two CL fields, or two TE fields are always `AMBIGUOUS`, even
  if duplicate values agree. A single CL value after SP/HTAB trimming is one or
  more decimal digits (leading zeros allowed); no sign, commas, hex or suffix.
  A syntactically valid length exceeding maxBodyBytes is `LIMIT`, with no body
  needed. Avoid overflow/precision loss for arbitrarily many digits.
- TE supports exactly one case-insensitive `chunked` value after trimming. Other
  values, lists, parameters and coding sequences are `SYNTAX`. Without CL/TE the
  request has zero body bytes regardless of method; following bytes start the
  next request. Do not infer length from EOF or method.
- A chunk size is one or more hexadecimal digits followed immediately by CRLF.
  No spaces, signs, `0x`, or chunk extensions in this deliberately strict subset.
  Leading zeros and upper/lowercase hex are valid. Nonzero sizes are followed by
  exactly that many uninterpreted bytes and CRLF. A zero chunk is followed directly
  by the trailer section, ending in an empty CRLF line. Empty trailers are thus
  `0\r\n\r\n`. Trailer field grammar matches header field grammar, but Host, CL
  and TE are forbidden (`SYNTAX`). A Trailer declaration header is not required.

## Limits and terminal errors

- maxHeaderBytes applies independently to (a) the whole request line and header
  section including its final CRLFCRLF, (b) EACH chunk-size line including CRLF,
  and (c) the entire trailer section including its final empty CRLF. Equality is
  allowed. Empty trailers count as 2 bytes; `0\r\n` belongs to the size line, not
  trailers. Data chunks and subsequent requests do not count toward header limits.
- maxBodyBytes applies to the total decoded body of ONE request, not buffered
  input or a whole pipeline. Equality is allowed. For chunked data, reject as soon
  as a complete size line advertises more than the remaining body allowance.
- Reject a line/section once its already-received byte count exceeds its limit,
  even without a terminator. Do not wait for EOF or additional input once malformed
  syntax or a size violation is decidable. Do not assign a required precedence
  when the same input simultaneously violates different rules.
- Throw `ParseError` with `code` in `SYNTAX`, `AMBIGUOUS`, `LIMIT`, `TRUNCATED`,
  or `CLOSED`; error message wording is not fixed. On a failure during push,
  `.completed` contains the full messages completed earlier IN THAT PUSH only,
  in order; messages returned by prior successful pushes are not repeated.
  Never return the invalid or incomplete message. After any ParseError, all
  subsequent push/end calls throw the same exception object, retaining its
  original completed array. No resynchronization after malformed input.
- EOF inside a request line, fields, fixed body, chunk-size/data/CRLF or trailers
  yields TRUNCATED. Normal EOF closes the decoder; repeated end returns `[]`.
  A push after normal EOF throws CLOSED and makes that error sticky as above.

This is a specified parsing subset inspired by HTTP/1.1; it intentionally rejects
some forms a general HTTP implementation might support. The task contract above
takes precedence; do not silently relax it to match another parser.

## Development

`src/decoder.js` coordinates framing, `src/headers.js` reads fields and chooses
body framing, `src/chunks.js` decodes chunks, and `src/errors.js` defines errors.
You may reorganize internals inside src while retaining the public API. Keep
`tests/compat.test.js` unchanged; add focused regression tests and a `demo.js`.
Use only built-in Node modules; do not delegate parsing to `node:http` or a native
HTTP parser. No network, package installation or running server is needed.

```sh
npm test
node demo.js
```
