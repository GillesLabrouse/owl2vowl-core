import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';

const [before, after, output] = process.argv.slice(2);
assert(before && after && output, 'Usage: node scripts/compare-conversions.mjs BEFORE AFTER REPORT');
const cases = ['named-some', 'named-all', 'object-exact-positive', 'object-min-positive',
  'object-max-positive', 'data-exact-positive', 'data-min-positive', 'data-max-positive'];

// Compare the complete JSON graphs by their semantic references, not the IDs
// assigned during traversal or the order of OWLAPI's sets.
function normalize(root) {
  const nodes = new Map(root.class.map(n => [n.id, n]));
  const attributes = new Map(root.classAttribute.map(n => [n.id, n]));
  function nodeName(id) {
    const n = attributes.get(id);
    assert(n, `Unknown node reference ${id}`);
    if (n.iri) return `${nodes.get(id).type}:${n.iri}`;
    return `${nodes.get(id).type}:${['intersection', 'union', 'complement'].map(k =>
      (n[k] || []).map(nodeName).sort().join(',')).join('|')}`;
  }
  const names = new Map([...attributes.keys()].map(id => [id, nodeName(id)]));
  for (const p of root.propertyAttribute) {
    const type = root.property.find(n => n.id === p.id).type;
    names.set(p.id, `${type}:${p.iri || ''}:${nodeName(p.domain)}:${nodeName(p.range)}`);
  }
  const references = new Set(['domain', 'range', 'inverse', 'superClasses', 'subClasses',
    'union', 'intersection', 'complement', 'disjointUnion', 'equivalent', 'superproperty', 'subproperty']);
  function visit(value, field) {
    if (Array.isArray(value)) return value.map(v => visit(v, field))
      .sort((a, b) => JSON.stringify(a).localeCompare(JSON.stringify(b)));
    if (value && typeof value === 'object') return Object.fromEntries(Object.keys(value).sort()
      .filter(k => k !== '_comment').map(k => [k, visit(value[k], k)]));
    return (field === 'id' || references.has(field)) && names.has(value) ? names.get(value) : value;
  }
  return visit(root);
}

for (const name of cases) {
  const oldJson = JSON.parse(fs.readFileSync(path.join(before, name + '.json')));
  const newJson = JSON.parse(fs.readFileSync(path.join(after, name + '.json')));
  assert.deepEqual(normalize(newJson), normalize(oldJson), `Regression in ${name}`);
}
fs.writeFileSync(output, JSON.stringify({baseline: '1.0.1', unchangedGraphs: cases.length, cases}, null, 2));
console.log(`${cases.length} complete baseline graphs preserved`);
