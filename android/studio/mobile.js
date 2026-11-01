document.addEventListener('click',e=>{
 if(!document.documentElement.classList.contains('android-history'))return;
 const panel=document.querySelector('[data-workspace-sidebar-panel]');
 if(!panel || !panel.contains(e.target) || e.target.closest('button,[role=button],[data-task-item-key]'))document.documentElement.classList.remove('android-history');
},true);
