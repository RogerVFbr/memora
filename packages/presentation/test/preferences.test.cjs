const { test } = require('node:test');
const assert = require('node:assert/strict');
const { createDefaults, loadPreferences, savePreferences } = require('../../../.local/test-presentation/model.js');

const catalog={scenes:[],collections:[{id:'one',name:'One',description:'Demo',cover:{id:'p',src:'p.jpg',alt:'photo',width:10,height:10}}]};
const memory=new Map();
Object.defineProperty(globalThis,'localStorage',{value:{getItem:(key)=>memory.get(key)??null,setItem:(key,value)=>memory.set(key,value)},configurable:true});
test('version one round trip preserves non-default preferences and collection choices',()=>{
 const chosen={...createDefaults(catalog),theme:'gallery',fit:'cover',duration:20,metadata:false,motion:false,activeCollections:[]};
 assert.equal(savePreferences(chosen,'prefs'),true);assert.deepEqual(loadPreferences('prefs',catalog),chosen);
 assert.equal(JSON.parse(memory.get('prefs')).version,1);
});
test('malformed values recover, unknown collections are filtered and absent fields use defaults',()=>{
 for(const text of ['bad json','null','{"version":1,"preferences":null}','{"version":0}']) {
  memory.set('invalid',text);assert.deepEqual(loadPreferences('invalid',catalog),createDefaults(catalog));
 }
 memory.set('invalid',JSON.stringify({version:1,preferences:{theme:'other',duration:-1,activeCollections:['one','missing',null]}}));
 assert.deepEqual(loadPreferences('invalid',catalog),createDefaults(catalog));
});
test('future schemas are not overwritten by this application',()=>{
 const future=JSON.stringify({version:2,preferences:{newField:true}});memory.set('future',future);
 assert.deepEqual(loadPreferences('future',catalog),createDefaults(catalog));
 assert.equal(savePreferences(createDefaults(catalog),'future'),false);assert.equal(memory.get('future'),future);
});
test('storage failure does not prevent presentation',()=>{
 const original=globalThis.localStorage;
 Object.defineProperty(globalThis,'localStorage',{value:{getItem(){throw Error('unavailable')},setItem(){throw Error('unavailable')}},configurable:true});
 assert.deepEqual(loadPreferences('none',catalog),createDefaults(catalog));assert.equal(savePreferences(createDefaults(catalog),'none'),false);
 Object.defineProperty(globalThis,'localStorage',{value:original,configurable:true});
});
