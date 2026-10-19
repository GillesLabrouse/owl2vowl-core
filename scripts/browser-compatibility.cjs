// Verify converter fixtures in an unmodified WIDOCO WebVOWL installation.
// Install Playwright separately and select its browser with PLAYWRIGHT_CHANNEL.
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const {pathToFileURL} = require('node:url');
const {chromium} = require('playwright');

(async () => {
  const [assets, fixtures, output] = process.argv.slice(2);
  assert(assets && fixtures && output, 'Usage: node scripts/browser-compatibility.cjs ASSETS FIXTURES REPORT');
  const browser = await chromium.launch({channel: process.env.PLAYWRIGHT_CHANNEL, headless: true});
  const results = [];
  try {
    for (const file of fs.readdirSync(fixtures).filter(f => f.endsWith('.json')).sort()) {
      const json = JSON.parse(fs.readFileSync(path.join(fixtures, file), 'utf8'));
      const page = await browser.newPage({viewport: {width: 1400, height: 1000}});
      const errors = [];
      page.on('pageerror', error => errors.push(error.message));
      // The existing viewer references Google Fonts. Block external resources to
      // check that rendering the local JSON never depends on a service.
      await page.route(/^https?:/, route => route.abort());
      await page.route('**/data/ontology.json', route => route.fulfill({contentType: 'application/json', body: JSON.stringify(json)}));
      await page.goto(pathToFileURL(path.resolve(assets, 'index.html')).href + '#ontology');
      await page.waitForFunction(() => document.querySelectorAll('svg .node').length >= 3);
      // Ensure the existing display filter does not hide boolean operators.
      await page.locator('#setoperatorFilterCheckbox').evaluate(input => { if (input.checked) input.click(); });
      if (file.startsWith('intersection-') || file === 'nested-intersections.json') {
        await page.waitForFunction(() => [...document.querySelectorAll('svg .node')].some(n =>
          typeof n.__data__?.type === 'function' && ['owl:intersectionOf', 'owl:unionOf'].includes(n.__data__.type())),
          null, {timeout: 10000});
      }
      const expected = json.propertyAttribute.map(p => p.cardinality ||
        (p.minCardinality || p.maxCardinality ? `${p.minCardinality || '*'}..${p.maxCardinality || '*'}` : '')).filter(Boolean);
      await page.waitForFunction(expected => {
        const actual = [...document.querySelectorAll('svg text.cardinality')].map(n => n.textContent);
        return expected.every(text => actual.filter(t => t === text).length >= expected.filter(t => t === text).length);
      }, expected, {timeout: 15000});
      const rendered = await page.evaluate(() => ({
        nodes: document.querySelectorAll('svg .node').length,
        labels: document.querySelectorAll('svg .label').length,
        cardinalities: [...document.querySelectorAll('svg text.cardinality')].map(n => n.textContent),
        cardinalityEdges: [...document.querySelectorAll('svg g.cardinality')].filter(n =>
          typeof n.__data__?.id === 'function').map(n => ({id: String(n.__data__.id()),
            domain: String(n.__data__.domain().id()), range: String(n.__data__.range().id())})),
        operators: [...document.querySelectorAll('svg .node')].filter(n =>
          typeof n.__data__?.type === 'function' && ['owl:intersectionOf', 'owl:unionOf'].includes(n.__data__.type())).length,
        text: [...document.querySelectorAll('svg')].map(n => n.textContent).join('\n')
      }));
      assert.deepEqual(errors, [], `${file}: browser errors`);
      for (const p of json.propertyAttribute.filter(p => p.cardinality || p.minCardinality || p.maxCardinality)) {
        const edge = rendered.cardinalityEdges.find(e => e.id === p.id);
        assert(edge, `${file}: missing rendered restriction ${p.id}`);
        assert.equal(edge.domain, p.domain, `${file}: restriction domain changed in the viewer`);
        assert.equal(edge.range, p.range, `${file}: restriction range changed in the viewer`);
      }
      if (file === 'unrestricted.json') assert.deepEqual(rendered.cardinalities, []);
      if (file.startsWith('intersection-') || file === 'nested-intersections.json')
        assert(rendered.operators > 0, `${file}: missing boolean operator`);
      rendered.exports = [];
      if (['scoped-bounds.json', 'urn-imports.json', 'nested-intersections.json'].includes(file)) {
        for (const id of ['exportJson', 'exportSvg']) {
          const ready = page.waitForEvent('download');
          await page.locator('#' + id).evaluate(link => link.click());
          const download = await ready;
          const contents = fs.readFileSync(await download.path(), 'utf8');
          if (id === 'exportJson') {
            const exported = JSON.parse(contents);
            for (const property of json.propertyAttribute) {
              const copy = exported.propertyAttribute.find(p => p.id === property.id);
              assert(copy, `${file}: export lost property ${property.id}`);
              for (const field of ['iri', 'domain', 'range', 'cardinality', 'minCardinality', 'maxCardinality'])
                assert.equal(copy[field] || '', property[field] || '', `${file}: exported ${field}`);
            }
          } else {
            assert(contents.includes('<svg'), `${file}: invalid SVG export`);
          }
          rendered.exports.push({type: id, bytes: Buffer.byteLength(contents)});
        }
      }
      results.push({fixture: file, expectedCardinalities: expected, ...rendered, errors});
      if (file === 'scoped-bounds.json') await page.screenshot({path: output.replace(/\.json$/, '.png')});
      await page.close();
    }
    fs.writeFileSync(output, JSON.stringify({browser: browser.version(), assets: path.resolve(assets), results}, null, 2));
    console.log(`${results.length} viewer fixtures passed`);
  } finally {
    await browser.close();
  }
})().catch(error => {console.error(error); process.exitCode = 1;});
