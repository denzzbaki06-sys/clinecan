import test from 'node:test';
import assert from 'node:assert/strict';
import {executionResult,runExecution,parseExecution,parseProgress,liveSteps,terminalEvent,artifactUrl,previewUrl} from '../src/services/executionApi.ts';
const id='12345678-1234-1234-1234-123456789abc';
const progress=(sequence,stage='BUILD',status='RUNNING')=>({sequence,executionId:id,timestamp:'2026-09-28T12:00:00Z',stage,status,message:'Real event'});
const state=(status='RUNNING')=>({id,status,generationMode:'DEMO',events:[],result:null,build:null,preview:{available:false,kind:'SANDBOXED_IFRAME',downloadUrl:null,bytes:0,entryUrl:null},error:null,repairAttempts:0});
class Stream {
 listeners={};onerror=null;closed=false;
 addEventListener(type,fn){this.listeners[type]=fn;}
 close(){this.closed=true;}
 emit(type,data){this.listeners[type]?.({data:JSON.stringify(data)});}
}
test('live steps and terminal reflect build and repair events',()=>{
 const events=[progress(1,'BUILD','FAILED'),progress(2,'DIAGNOSE','COMPLETED'),progress(3,'REPAIR'),progress(4,'REBUILD','COMPLETED')];
 const steps=liveSteps(events);assert.equal(steps.find(x=>x.name==='REPAIR').status,'RUNNING');assert.equal(steps.find(x=>x.name==='REBUILD').status,'COMPLETED');assert.equal(terminalEvent(events[0]).error,true);
});
test('strict execution/event parsing rejects mismatched and unsafe values',()=>{
 assert.throws(()=>parseProgress(progress(1),'other'));
 assert.throws(()=>parseProgress({...progress(1),sequence:101},id));
 assert.throws(()=>parseExecution({...state(),repairAttempts:3}));
 assert.throws(()=>parseExecution({...state(),preview:{available:true,kind:'SANDBOXED_IFRAME',downloadUrl:'javascript:bad',bytes:1,entryUrl:'javascript:bad'}}));
 assert.equal(parseExecution({...state(),generationMode:'LLM'}).generationMode,'LLM');
});
test('only successful builds expose artifact download',()=>{
 assert.equal(artifactUrl(state()),null);
 const s={...state('COMPLETED'),build:{success:true,durationMs:1},preview:{available:true,kind:'SANDBOXED_IFRAME',bytes:5,downloadUrl:`/api/agent/executions/${id}/artifact`,entryUrl:`/api/preview/${id}/${'a'.repeat(64)}/index.html`}};
 assert.match(artifactUrl(parseExecution(s)),new RegExp(`${id}/artifact$`));
 assert.equal(artifactUrl({...s,status:'FAILED'}),null);
 assert.match(previewUrl(parseExecution(s)),new RegExp(`/api/preview/${id}/a{64}/index\\.html$`));
});
test('prompt creates execution, SSE updates live, duplicates ignored, stream terminates',async()=>{
 const saved=globalThis.fetch;const stream=new Stream();const events=[];let request;
 globalThis.fetch=async(url,options)=>{request=options;return new Response(JSON.stringify(state()),{status:202});};
 try{
  const pending=runExecution('task app',e=>events.push(e),{open:()=>stream});await new Promise(resolve=>setImmediate(resolve));
  assert.equal(JSON.parse(request.body).prompt,'task app');
  stream.emit('progress',progress(1));stream.emit('progress',progress(1));assert.equal(events.length,1);
  stream.emit('complete',{...state('COMPLETED'),events:[progress(1),progress(2,'BUILD','COMPLETED')]});
  const result=await pending;assert.equal(result.status,'COMPLETED');assert.equal(events.length,2);assert.equal(stream.closed,true);
 }finally{globalThis.fetch=saved;}
});
for(const status of ['FAILED','TIMED_OUT','CANCELLED']) test(`terminal ${status} closes stream honestly`,async()=>{
 const saved=globalThis.fetch;const stream=new Stream();globalThis.fetch=async()=>new Response(JSON.stringify(state()),{status:202});
 try{const pending=runExecution('task',()=>{},{open:()=>stream});await new Promise(r=>setImmediate(r));stream.emit('complete',{...state(status),error:{code:status,message:'Stopped',retryable:false}});assert.equal((await pending).status,status);assert.equal(stream.closed,true);}finally{globalThis.fetch=saved;}
});
test('connection loss recovers final state without duplicate events',async()=>{
 const saved=globalThis.fetch;const stream=new Stream();let calls=0;
 globalThis.fetch=async()=>new Response(JSON.stringify(++calls===1?state():{...state('COMPLETED'),events:[progress(1)]}),{status:200});
 try{const pending=runExecution('task',()=>{},{open:()=>stream});await new Promise(r=>setImmediate(r));stream.onerror({});assert.equal((await pending).status,'COMPLETED');assert.equal(stream.closed,true);}finally{globalThis.fetch=saved;}
});
test('client timeout cancels server execution and closes events',async()=>{
 const saved=globalThis.fetch;const stream=new Stream();let cancelled=false;
 globalThis.fetch=async(_url,options)=>{if(options?.method==='DELETE')cancelled=true;return new Response(JSON.stringify(state()),{status:202});};
 try{await assert.rejects(runExecution('task',()=>{},{open:()=>stream,timeoutMs:15}),/zaman aşımına/);assert.equal(cancelled,true);assert.equal(stream.closed,true);}finally{globalThis.fetch=saved;}
});
test('server capacity produces helpful error',async()=>{
 const saved=globalThis.fetch;globalThis.fetch=async()=>new Response('{}',{status:429});
 try{await assert.rejects(runExecution('task',()=>{}),/meşgul/);}finally{globalThis.fetch=saved;}
});

test('history result keeps build failure and full live stages',()=>{
 const snapshot={...state('FAILED'),error:{code:'COMPILATION',message:'Build failed',retryable:false},events:[progress(1,'BUILD','FAILED')],result:{projectName:'task',status:'COMPLETED',execution:{id,events:[]},steps:[],error:null}};
 const result=executionResult(snapshot);assert.equal(result.status,'FAILED');assert.equal(result.error.code,'COMPILATION');assert.equal(result.steps.find(s=>s.name==='BUILD').status,'FAILED');
 assert.equal(result.execution.events[0].stage,'BUILD');
});
