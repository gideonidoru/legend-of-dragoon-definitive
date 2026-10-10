/* Original offline explorer behavior fixtures; AGPL v3, see LICENSE. */
'use strict';
const fs = require('node:fs'), vm = require('node:vm'), assert = require('node:assert/strict');
const path = require('node:path');
const html = fs.readFileSync(path.join(__dirname, '../docs/definitive/model-catalog/index.html'), 'utf8');
const payload = html.match(/<script type="application\/json" id="catalog">([\s\S]*?)<\/script>/)[1];
const script = html.match(/<script>\s*([\s\S]*?)<\/script>/)[1];
class Element {
  constructor(tag) { this.tag = tag; this.children = []; this.listeners = {}; this.value = ''; this.textContent = ''; }
  append(...nodes) { this.children.push(...nodes); }
  replaceChildren(...nodes) { this.children = nodes; }
  addEventListener(name, callback) { this.listeners[name] = callback; }
  fire(name) { this.listeners[name](); }
}
const ids = Object.fromEntries(['catalog','stats','bucket','identity','search','rows','count','page','previous','next'].map(id=>[id,new Element('div')]));
ids.catalog.textContent = payload;
const context = vm.createContext({document:{getElementById:id=>ids[id],createElement:tag=>new Element(tag)}, console});
vm.runInContext(script, context, {timeout:30000});
assert.equal(ids.rows.children.length,40);
assert.equal(ids.previous.disabled,true);
ids.next.fire('click');
assert.equal(ids.page.textContent,'2 / 34');
ids.bucket.value='1-party'; ids.bucket.fire('input');
assert.equal(ids.rows.children.length,10);
assert.equal(ids.next.disabled,true);
ids.identity.value='unknown'; ids.identity.fire('input');
assert.equal(ids.rows.children.length,0);
ids.bucket.value=''; ids.bucket.fire('input');
assert.equal(ids.rows.children.length,30);
ids.identity.value=''; ids.search.value='SECT/DRGN0.BIN/337'; ids.search.fire('input');
assert.equal(ids.rows.children.length,1);
const entry = ids.rows.children[0]; entry.open=true; entry.fire('toggle');
const detail = entry.children[1]; assert.ok(detail.children.length>2);
const length=detail.children.length; entry.fire('toggle'); assert.equal(detail.children.length,length);
const data=JSON.parse(payload), row=data.models.find(r=>r.entityNames.includes('Claire'));
assert.ok(row);
ids.search.value=row.catalogId; ids.search.fire('input'); assert.equal(ids.rows.children.length,1);
assert.equal(ids.previous.disabled,true);
ids.search.value='no such model 8b72cc'; ids.search.fire('input');
assert.equal(ids.rows.children.length,0); assert.equal(ids.page.textContent,'1 / 1');
assert.equal(ids.next.disabled,true);
assert.ok(!html.includes('fetch('));
console.log('Offline explorer: pagination, combined filters, route search, lazy evidence, empty state passed.');
