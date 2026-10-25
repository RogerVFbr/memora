const { test } = require('node:test');
const assert = require('node:assert/strict');
const { initialPlayback, playbackReducer, isRunning, keepAwake } = require('../../../../.local/test-presentation/playback.js');

test('suspension cancels playback intent and resume never autoplays', () => {
  let state = playbackReducer(initialPlayback(true), {type:'command',command:'play'});
  assert.equal(isRunning(state,true),true);
  state = playbackReducer(state,{type:'visibility',visible:false});
  assert.equal(isRunning(state,true),false); assert.equal(keepAwake(state,true),false);
  state = playbackReducer(state,{type:'command',command:'play'});
  assert.equal(state.playing,false);
  state = playbackReducer(state,{type:'visibility',visible:true});
  assert.equal(state.playing,false); assert.equal(state.menu,true); assert.equal(state.panel,null);
});
test('menus suspend advance, back closes panel before controls, explicit pause releases screen', () => {
  let state=playbackReducer(initialPlayback(true),{type:'command',command:'play'});
  state=playbackReducer(state,{type:'command',command:'menu'});
  assert.equal(isRunning(state,true),false); assert.equal(keepAwake(state,true),true);
  state=playbackReducer(state,{type:'panel',value:'settings'});
  state=playbackReducer(state,{type:'command',command:'back'});
  assert.equal(state.panel,null);assert.equal(state.menu,true);
  state=playbackReducer(state,{type:'command',command:'back'});
  assert.equal(isRunning(state,true),true);
  state=playbackReducer(state,{type:'command',command:'pause'});
  assert.equal(isRunning(state,true),false);assert.equal(keepAwake(state,true),false);
});
test('duplicate visibility and unknown commands do not restart or reset active playback', () => {
  const state=playbackReducer(initialPlayback(true),{type:'command',command:'play'});
  assert.equal(playbackReducer(state,{type:'visibility',visible:true}),state);
  assert.equal(playbackReducer(state,{type:'command',command:'unknown'}),state);
  assert.equal(isRunning(state,false),false);
});
