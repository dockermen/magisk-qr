const fs = require('node:fs');

function decodeLog(text) {
  const pending = new Map();
  const events = [];
  let invalid = 0;
  for (const line of text.split(/\r?\n/)) {
    const match = line.match(/\bJYQR1 (\d+) (\d+) (\d+)\/(\d+) ([A-Za-z0-9+/=]+)\s*$/);
    if (!match) continue;
    const [, pid, id, partText, totalText, data] = match;
    const part = Number(partText), total = Number(totalText);
    if (part < 1 || part > total || total > 1024) { invalid++; continue; }
    const key = `${pid}:${id}`;
    if (!pending.has(key)) pending.set(key, { total, parts: new Map(), conflict: false });
    const record = pending.get(key);
    if (record.total !== total || (record.parts.has(part) && record.parts.get(part) !== data)) {
      record.conflict = true;
    }
    record.parts.set(part, data);
  }
  let incomplete = 0;
  for (const record of pending.values()) {
    if (record.conflict) { invalid++; continue; }
    if (record.parts.size !== record.total) { incomplete++; continue; }
    try {
      const encoded = Array.from({ length: record.total }, (_, i) => record.parts.get(i + 1)).join('');
      events.push(JSON.parse(Buffer.from(encoded, 'base64').toString('utf8')));
    } catch { invalid++; }
  }
  events.sort((a, b) => a.time_ms - b.time_ms);
  return { events, incomplete, invalid };
}

function readLog(path) {
  const data = fs.readFileSync(path);
  return data[0] === 0xff && data[1] === 0xfe
    ? data.subarray(2).toString('utf16le') : data.toString('utf8').replace(/^\uFEFF/, '');
}

if (require.main === module) {
  const [, , input, output] = process.argv;
  if (!input) {
    process.stderr.write('Usage: node tools/decode-log.cjs INPUT.log [OUTPUT.jsonl]\n');
    process.exitCode = 1;
  } else {
    const result = decodeLog(readLog(input));
    const jsonl = result.events.map(event => JSON.stringify(event)).join('\n');
    if (output) fs.writeFileSync(output, jsonl ? jsonl + '\n' : '', 'utf8');
    else if (jsonl) process.stdout.write(jsonl + '\n');
    process.stderr.write(`events=${result.events.length} incomplete=${result.incomplete} invalid=${result.invalid}\n`);
    if (result.invalid || result.incomplete) process.exitCode = 2;
  }
}

module.exports = { decodeLog, readLog };
