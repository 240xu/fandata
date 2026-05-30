/**
 * FanData 段评注入脚本 - 注入 WebView 实现段落评论
 * 功能：SVG 标记、坐标定位、点击浮层、评论加载/提交
 */
(function(){
'use strict';
const API='http://101.35.133.34:8888';
const style=document.createElement('style');
style.textContent=`
.fd-m{position:absolute;right:-28px;width:22px;height:22px;cursor:pointer;opacity:.35;transition:opacity .2s;z-index:100}
.fd-m:hover{opacity:1}
.fd-f{position:fixed;right:8px;width:300px;max-height:380px;background:#fff;border-radius:12px;box-shadow:0 4px 20px rgba(0,0,0,.15);z-index:1000;overflow:hidden;display:none}
.fd-f.show{display:flex;flex-direction:column}
.fd-h{padding:10px 14px;border-bottom:1px solid #eee;font-weight:bold;display:flex;justify-content:space-between}
.fd-x{cursor:pointer;font-size:18px;color:#999}
.fd-l{flex:1;overflow-y:auto;padding:6px 14px}
.fd-i{padding:6px 0;border-bottom:1px solid #f5f5f5}
.fd-i .a{font-size:11px;color:#888}
.fd-i .c{margin-top:3px;font-size:13px}
.fd-b{padding:6px 14px;border-top:1px solid #eee;display:flex;gap:6px}
.fd-t{flex:1;border:1px solid #ddd;border-radius:8px;padding:6px;font-size:13px;resize:none}
.fd-s{background:#4CAF50;color:#fff;border:none;border-radius:8px;padding:6px 14px;cursor:pointer}
`;
document.head.appendChild(style);
const f=document.createElement('div');f.className='fd-f';
f.innerHTML='<div class="fd-h"><span>段落评论</span><span class="fd-x" onclick="this.closest(\'.fd-f\').classList.remove(\'show\')">&times;</span></div><div class="fd-l" id="fdr-l"></div><div class="fd-b"><textarea class="fd-t" id="fdr-t" placeholder="发表评论..." rows="2"></textarea><button class="fd-s" id="fdr-s">发送</button></div>';
document.body.appendChild(f);
function init(){
document.querySelectorAll('p,.content-paragraph,[data-paragraph]').forEach((p,i)=>{
if(p.querySelector('.fd-m'))return;if(p.textContent.trim().length<10)return;
const m=document.createElement('div');m.className='fd-m';m.dataset.idx=i;
m.innerHTML='<svg viewBox="0 0 24 24" fill="none" stroke="#4CAF50" stroke-width="2"><path d="M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z"/></svg>';
m.addEventListener('click',e=>{e.stopPropagation();const r=m.getBoundingClientRect();f.style.top=Math.min(r.top,window.innerHeight-400)+'px';f.classList.add('show');loadR(i)});
p.style.position='relative';p.appendChild(m);
});
}
function loadR(i){
const l=document.getElementById('fdr-l');l.innerHTML='<div style="text-align:center;padding:16px;color:#999">加载中...</div>';
if(window.java&&window.java.ajax){
try{const bid=window.java.get('book_id')||'';const cid=window.java.get('chapter_id')||'';
const r=window.java.ajax(API+'/get_review?book_id='+bid+'&item_id='+cid+'&paragraph='+i);
const d=JSON.parse(r);if(d&&d.data&&d.data.length>0){l.innerHTML=d.data.map(r=>'<div class="fd-i"><div class="a">'+(r.user||'匿名')+'</div><div class="c">'+(r.content||'')+'</div></div>').join('')}
else{l.innerHTML='<div style="text-align:center;padding:16px;color:#999">暂无评论</div>'}}catch(e){l.innerHTML='<div style="text-align:center;padding:16px;color:#999">加载失败</div>'}
}}
document.getElementById('fdr-s').addEventListener('click',()=>{
const t=document.getElementById('fdr-t');const c=t.value.trim();if(!c)return;
if(window.java&&window.java.ajax){try{const bid=window.java.get('book_id')||'';const cid=window.java.get('chapter_id')||'';
window.java.post(API+'/add_review',JSON.stringify({book_id:bid,item_id:cid,content:c}));t.value='';loadR(0)}catch(e){}}});
if(document.readyState==='loading')document.addEventListener('DOMContentLoaded',init);else init();
})();