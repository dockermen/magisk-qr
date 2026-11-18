const { test } = require('node:test');
const assert = require('node:assert/strict');
const { decodeLog } = require('./decode-log.cjs');

function chunks(event, id = 10) {
  const b64 = Buffer.from(JSON.stringify(event)).toString('base64');
  const split = Math.floor(b64.length / 2);
  return [`I/JYQrHook: JYQR1 99 ${id} 1/2 ${b64.slice(0, split)}`,
    `I/JYQrHook: JYQR1 99 ${id} 2/2 ${b64.slice(split)}`];
}

test('reassembles interleaved chunks, Unicode and duplicate lines', () => {
  const a = { time_ms: 2, body: '手机确认', headers: [{ name: 'Cookie', value: 'fixture' }] };
  const b = { time_ms: 1, body: 'scan' };
  const x = chunks(a), y = chunks(b, 11);
  assert.deepEqual(decodeLog([x[1], y[0], x[0], x[0], y[1]].join('\n')),
    { events: [b, a], incomplete: 0, invalid: 0 });
});

test('reports incomplete chunks without emitting partial JSON', () => {
  assert.deepEqual(decodeLog(chunks({ time_ms: 1 })[0]),
    { events: [], incomplete: 1, invalid: 0 });
});

test('rejects contradictory chunks and malformed JSON', () => {
  const x = chunks({ time_ms: 1 });
  assert.equal(decodeLog([...x, x[0].replace(/ [A-Za-z0-9+/=]+$/, ' AAAA')].join('\n')).invalid, 1);
  assert.equal(decodeLog('JYQR1 99 1 1/1 bm90LWpzb24=').invalid, 1);
});
