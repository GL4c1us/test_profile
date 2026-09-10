(() => {
  'use strict';
  const ROOT_ID = 'plane-queue-root-v1';
  const FLOAT_ID = 'plane-queue-float-v1';
  const STYLE_ID = 'plane-queue-style-v1';
  const norm = v => String(v || '').replace(/\u00a0/g,' ').replace(/\s+/g,' ').trim();

  function fnv1a(str){let h=0x811c9dc5;for(let i=0;i<str.length;i++){h^=str.charCodeAt(i);h=Math.imul(h,0x01000193);}return(h>>>0).toString(16).padStart(8,'0');}
  function readDone(){try{const raw=window.PlaneQueueNative?PlaneQueueNative.getDone():'[]';const arr=JSON.parse(raw||'[]');return new Set(Array.isArray(arr)?arr:[]);}catch(_){try{return new Set(JSON.parse(localStorage.getItem('planeQueueDone')||'[]'));}catch(_){return new Set();}}}
  function writeDone(done){const raw=JSON.stringify(Array.from(done));try{if(window.PlaneQueueNative)PlaneQueueNative.saveDone(raw);}catch(_){}try{localStorage.setItem('planeQueueDone',raw);}catch(_){}}
  function clearDone(){try{if(window.PlaneQueueNative)PlaneQueueNative.clearDone();}catch(_){}try{localStorage.removeItem('planeQueueDone');}catch(_){}}

  function rowsFromDocument(doc){
    if(!doc)return[];
    const tables=Array.from(doc.querySelectorAll('table'));
    let best=[];
    for(const table of tables){
      const rows=Array.from(table.querySelectorAll('tr')).map((tr,domIndex)=>{
        let cells=Array.from(tr.querySelectorAll('td')).map(td=>norm(td.innerText||td.textContent)).filter(Boolean);
        cells=cells.filter((v,i)=>i===0||v!==cells[i-1]);
        return{cells,domIndex};
      }).filter(r=>r.cells.length>0&&norm(r.cells.join(' ')).length>1);
      if(rows.length>best.length)best=rows;
    }
    return best;
  }

  function gatherRows(){
    let rows=rowsFromDocument(document);
    for(const frame of Array.from(document.querySelectorAll('iframe'))){
      try{const inside=rowsFromDocument(frame.contentDocument);if(inside.length>rows.length)rows=inside;}catch(_){}
    }
    const tailRe=/\bN[0-9][0-9A-Z]{1,5}\b/i;
    const tailRows=rows.filter(r=>tailRe.test(r.cells.join(' ')));
    if(tailRows.length>=1)rows=tailRows;
    const seen=new Map();
    return rows.map((r,idx)=>{
      const text=r.cells.join(' • '),base=fnv1a(r.cells.join('\u241f')),occurrence=(seen.get(base)||0)+1;
      seen.set(base,occurrence);
      const match=text.match(tailRe),tail=match?match[0].toUpperCase():r.cells[0];
      const details=r.cells.filter(c=>norm(c).toUpperCase()!==tail.toUpperCase()).join(' • ');
      return{key:base+'-'+occurrence,originalOrder:idx+1,tail,details,text};
    }).filter(r=>{
      if(r.text.length<2)return false;
      if(/^(plane|aircraft|tail|tail number|registration|order|status|time|date)(\s*•.*)?$/i.test(r.text))return false;
      return true;
    });
  }

  let rows=gatherRows();
  let done=readDone(),lastUndoneKey=null,undoTimer=null;

  function ensureStyle(){
    if(document.getElementById(STYLE_ID))return;
    const style=document.createElement('style');
    style.id=STYLE_ID;
    style.textContent=`
#${ROOT_ID},#${ROOT_ID} *{box-sizing:border-box}
#${ROOT_ID}{position:fixed;inset:0;z-index:2147483646;overflow:auto;background:#0d1117;color:#f4f7fb;font-family:system-ui,-apple-system,BlinkMacSystemFont,"Segoe UI",sans-serif;padding:max(18px,env(safe-area-inset-top)) 14px max(24px,env(safe-area-inset-bottom))}
#${ROOT_ID} .pq-header{max-width:820px;margin:0 auto 14px}
#${ROOT_ID} .pq-title{font-size:28px;font-weight:800;letter-spacing:-.5px;margin:0 0 2px}
#${ROOT_ID} .pq-sub{opacity:.70;font-size:14px;margin-bottom:13px}
#${ROOT_ID} .pq-actions{display:flex;gap:8px;flex-wrap:wrap}
#${ROOT_ID} button{border:0;border-radius:12px;padding:10px 13px;font:inherit;font-weight:700;cursor:pointer}
#${ROOT_ID} .pq-primary{background:#f0f6fc;color:#111820}
#${ROOT_ID} .pq-secondary{background:#21262d;color:#f0f6fc;border:1px solid #30363d}
#${ROOT_ID} .pq-danger{background:#341a1f;color:#ffb4b8;border:1px solid #6e2a34}
#${ROOT_ID} .pq-list{max-width:820px;margin:0 auto;display:grid;gap:10px}
#${ROOT_ID} .pq-card{display:grid;grid-template-columns:48px 1fr 52px;align-items:center;gap:10px;min-height:78px;padding:12px 10px 12px 14px;background:#161b22;border:1px solid #30363d;border-radius:15px;box-shadow:0 5px 20px rgba(0,0,0,.18)}
#${ROOT_ID} .pq-order{font-size:22px;font-weight:850;opacity:.72;text-align:center}
#${ROOT_ID} .pq-tail{font-size:23px;line-height:1.08;font-weight:850;letter-spacing:.2px}
#${ROOT_ID} .pq-details{margin-top:5px;font-size:14px;line-height:1.35;color:#aeb8c4;overflow-wrap:anywhere}
#${ROOT_ID} .pq-check{width:46px;height:46px;border-radius:50%;background:#1f6f43;color:white;font-size:24px;display:grid;place-items:center;padding:0}
#${ROOT_ID} .pq-empty{margin:48px auto;max-width:520px;text-align:center;color:#c9d1d9}
#${ROOT_ID} .pq-empty strong{display:block;font-size:26px;margin-bottom:8px;color:white}
#${ROOT_ID} .pq-undo{position:fixed;left:50%;transform:translateX(-50%);bottom:max(16px,env(safe-area-inset-bottom));z-index:2147483647;display:none;align-items:center;gap:14px;background:#f0f6fc;color:#111820;border-radius:14px;padding:10px 12px 10px 16px;box-shadow:0 10px 40px rgba(0,0,0,.4);font-weight:650}
#${ROOT_ID} .pq-undo button{background:#1f6feb;color:white;padding:8px 12px}
#${FLOAT_ID}{position:fixed;right:14px;bottom:18px;z-index:2147483647;display:none;border:0;border-radius:999px;padding:12px 17px;background:#111820;color:white;font:700 15px system-ui;box-shadow:0 6px 24px rgba(0,0,0,.35)}
@media(max-width:460px){#${ROOT_ID} .pq-card{grid-template-columns:42px 1fr 48px;padding-left:8px;gap:7px}#${ROOT_ID} .pq-tail{font-size:21px}#${ROOT_ID} .pq-order{font-size:19px}}
html.pq-pip #${ROOT_ID}{padding:6px 8px;overflow:hidden}
html.pq-pip #${ROOT_ID} .pq-header{margin:0 auto 5px}
html.pq-pip #${ROOT_ID} .pq-title{font-size:14px;letter-spacing:0;margin:0}
html.pq-pip #${ROOT_ID} .pq-sub,html.pq-pip #${ROOT_ID} .pq-actions,html.pq-pip #${ROOT_ID} .pq-undo{display:none!important}
html.pq-pip #${ROOT_ID} .pq-list{gap:3px}
html.pq-pip #${ROOT_ID} .pq-card{grid-template-columns:20px 1fr;gap:4px;min-height:0;padding:4px 6px;border-radius:6px;box-shadow:none}
html.pq-pip #${ROOT_ID} .pq-card:nth-child(n+4){display:none}
html.pq-pip #${ROOT_ID} .pq-order{font-size:11px}
html.pq-pip #${ROOT_ID} .pq-tail{font-size:14px;line-height:1.05}
html.pq-pip #${ROOT_ID} .pq-details,html.pq-pip #${ROOT_ID} .pq-check{display:none}
html.pq-pip #${ROOT_ID} .pq-empty{margin:18px auto;font-size:11px}
html.pq-pip #${ROOT_ID} .pq-empty strong{font-size:17px;margin-bottom:2px}
`;
    document.documentElement.appendChild(style);
  }

  function esc(s){return String(s||'').replace(/[&<>"']/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));}

  function render(){
    ensureStyle();
    let root=document.getElementById(ROOT_ID);
    if(!root){root=document.createElement('div');root.id=ROOT_ID;document.documentElement.appendChild(root);}
    let floatBtn=document.getElementById(FLOAT_ID);
    if(!floatBtn){floatBtn=document.createElement('button');floatBtn.id=FLOAT_ID;floatBtn.textContent='Queue';floatBtn.onclick=()=>{root.style.display='block';floatBtn.style.display='none';};document.documentElement.appendChild(floatBtn);}

    const remaining=rows.filter(r=>!done.has(r.key));
    root.innerHTML=`<div class="pq-header"><div class="pq-title">Plane Queue</div><div class="pq-sub"><span>${remaining.length}</span> remaining • ${done.size} completed locally</div><div class="pq-actions"><button class="pq-primary" id="pqRefresh">Refresh Sheet</button><button class="pq-secondary" id="pqPip">Pop out</button><button class="pq-secondary" id="pqSheet">View Sheet</button><button class="pq-danger" id="pqReset">Reset Done</button></div></div><div class="pq-list" id="pqList"></div><div class="pq-undo" id="pqUndo"><span>Plane removed</span><button id="pqUndoBtn">Undo</button></div>`;

    const list=root.querySelector('#pqList');
    if(!remaining.length){
      const note=rows.length ? 'Everything currently in the sheet has been checked off.' : 'No planes are currently waiting in the queue.';
      list.innerHTML=`<div class="pq-empty"><strong>Queue empty</strong>${note}</div>`;
    }else{
      remaining.forEach((r,visibleIndex)=>{
        const card=document.createElement('div');card.className='pq-card';
        const st=esc(r.tail),sd=esc(r.details);
        card.innerHTML=`<div class="pq-order">${visibleIndex+1}</div><div><div class="pq-tail">${st}</div>${sd?`<div class="pq-details">${sd}</div>`:''}</div><button class="pq-check" aria-label="Complete ${st}" title="Complete">✓</button>`;
        card.querySelector('.pq-check').onclick=()=>complete(r.key,card);
        list.appendChild(card);
      });
    }

    root.querySelector('#pqRefresh').onclick=()=>location.reload();
    root.querySelector('#pqPip').onclick=()=>{try{if(window.PlaneQueueNative&&PlaneQueueNative.enterPip){PlaneQueueNative.enterPip();}else{alert('Picture-in-picture is not available on this device.');}}catch(_){alert('Picture-in-picture is not available on this device.');}};
    root.querySelector('#pqSheet').onclick=()=>{root.style.display='none';floatBtn.style.display='block';};
    root.querySelector('#pqReset').onclick=()=>{if(confirm('Put all completed planes back in the queue?')){done=new Set();clearDone();render();}};
    root.querySelector('#pqUndoBtn').onclick=undoLast;
  }

  function complete(key,card){lastUndoneKey=key;done.add(key);writeDone(done);card.style.transition='opacity .18s ease, transform .18s ease';card.style.opacity='0';card.style.transform='translateX(18px)';setTimeout(render,190);setTimeout(showUndo,230);}
  function showUndo(){const undo=document.getElementById('pqUndo');if(!undo||!lastUndoneKey)return;undo.style.display='flex';if(undoTimer)clearTimeout(undoTimer);undoTimer=setTimeout(()=>{const u=document.getElementById('pqUndo');if(u)u.style.display='none';lastUndoneKey=null;},5000);}
  function undoLast(){if(!lastUndoneKey)return;done.delete(lastUndoneKey);writeDone(done);lastUndoneKey=null;if(undoTimer)clearTimeout(undoTimer);render();}

  render();
})();
