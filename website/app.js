// Optional GitHub Pages hosting: compute the repository URL from the
// hosting account rather than shipping a personal username in source.
const hostMatch = window.location.hostname.match(/^([a-z0-9-]+)\\.github\\.io$/i);
if (hostMatch) {
  const repository = 'https://github.com/' + hostMatch[1] + '/Cloudix';
  document.querySelectorAll('a[href^="https://github.com/search?q=Cloudix"]').forEach(link => {
    const old = link.getAttribute('href') || '';
    link.href = repository + (old.includes('issues') ? '/issues' : '');
  });
}
const reducedMotion = window.matchMedia('(prefers-reduced-motion: reduce)');
const touchOrSmall = window.matchMedia('(max-width: 700px)');
const header = document.getElementById('site-header');
const toggle = document.getElementById('menu-toggle');
const menu = document.getElementById('site-nav');
const progress = document.getElementById('page-progress');
const art = document.querySelector('.hero-art');
const year = document.getElementById('year');
if (year) year.textContent = String(new Date().getFullYear());

function closeMenu() {
  document.body.classList.remove('menu-open');
  toggle?.setAttribute('aria-expanded', 'false');
  toggle?.setAttribute('aria-label', 'Ouvrir le menu');
}
toggle?.addEventListener('click', () => {
  const opening = !document.body.classList.contains('menu-open');
  document.body.classList.toggle('menu-open', opening);
  toggle.setAttribute('aria-expanded', String(opening));
  toggle.setAttribute('aria-label', opening ? 'Fermer le menu' : 'Ouvrir le menu');
});
menu?.querySelectorAll('a').forEach(a => a.addEventListener('click', closeMenu));
document.addEventListener('keydown', e => { if (e.key === 'Escape') closeMenu(); });
window.addEventListener('resize', () => { if (innerWidth > 640) closeMenu(); }, { passive: true });

let scrollPending = false;
function handleScroll() {
  if (scrollPending) return;
  scrollPending = true;
  requestAnimationFrame(() => {
    const max = Math.max(1, document.documentElement.scrollHeight - innerHeight);
    progress.style.transform = `scaleX(${Math.min(1, scrollY / max)})`;
    header?.classList.toggle('scrolled', scrollY > 24);
    scrollPending = false;
  });
}
addEventListener('scroll', handleScroll, { passive: true });
handleScroll();

const cursor = document.getElementById('cursor-dot');
if (cursor && matchMedia('(pointer:fine)').matches && !reducedMotion.matches) {
  let x = -100, y = -100;
  addEventListener('pointermove', e => {
    x = e.clientX; y = e.clientY;
    cursor.style.transform = `translate3d(${x - 5}px,${y - 5}px,0)`;
    cursor.style.opacity = '1';
  }, { passive: true });
  document.querySelectorAll('a,button').forEach(el => {
    el.addEventListener('mouseenter', () => cursor.classList.add('active'));
    el.addEventListener('mouseleave', () => cursor.classList.remove('active'));
  });
  document.addEventListener('mouseleave', () => cursor.style.opacity = '0');
}

// Enhancements never block the content or the real links.
window.addEventListener('load', () => {
  if (reducedMotion.matches) return;
  const gsap = window.gsap;
  const ScrollTrigger = window.ScrollTrigger;
  const Lenis = window.Lenis;
  if (gsap && ScrollTrigger) {
    gsap.registerPlugin(ScrollTrigger);
    gsap.utils.toArray('.section-title, .final-title, .benefit, .step, .spec, .section-head-main>p, .diagram').forEach(el => {
      gsap.fromTo(el, { opacity: .2, y: 35 }, {
        opacity: 1, y: 0, duration: 1.05, ease: 'power3.out',
        scrollTrigger: { trigger: el, start: 'top 91%', once: true }
      });
    });
    gsap.fromTo('.hero-content > *', { opacity: 0, y: 20 }, {
      opacity: 1, y: 0, duration: .85, stagger: .12, ease: 'power3.out', delay: .08
    });
    if (Lenis) {
      const lenis = new Lenis({ duration: 1.05, smoothWheel: true, wheelMultiplier: .85, touchMultiplier: 1 });
      lenis.on('scroll', ScrollTrigger.update);
      gsap.ticker.add(time => lenis.raf(time * 1000));
      gsap.ticker.lagSmoothing(0);
      document.querySelectorAll('a[href^="#"]').forEach(link => link.addEventListener('click', event => {
        const target = document.querySelector(link.getAttribute('href'));
        if (!target) return;
        event.preventDefault();
        lenis.scrollTo(target, { offset: -68, duration: .95 });
        history.replaceState(null, '', link.getAttribute('href'));
      }));
    }
  } else {
    const observer = new IntersectionObserver(entries => {
      for (const entry of entries) {
        if (entry.isIntersecting) {
          entry.target.classList.add('in-view');
          observer.unobserve(entry.target);
        }
      }
    }, { threshold: .15 });
    document.querySelectorAll('.step,.benefit,.spec').forEach(el => observer.observe(el));
  }
});

// Three.js is requested only when the hero becomes visible; CSS phone works offline.
let booted = false;
const observer = new IntersectionObserver(entries => {
  if (entries[0]?.isIntersecting && !booted) {
    booted = true;
    observer.disconnect();
    bootThree().catch(err => { console.info('Cloudix: aperçu statique utilisé.', err?.message || err); });
  }
}, { rootMargin: '200px' });
if (art) observer.observe(art);

async function bootThree() {
  if (!art || !('WebGLRenderingContext' in window)) return;
  // Reduced motion receives the same visual in a fixed 3D orientation.
  const THREE = await import('https://cdn.jsdelivr.net/npm/three@0.160.1/build/three.module.js');
  const canvas = document.getElementById('phone-canvas');
  if (!canvas) return;
  let renderer;
  try { renderer = new THREE.WebGLRenderer({ canvas, alpha: true, antialias: !touchOrSmall.matches, powerPreference: 'low-power' }); }
  catch { return; }
  renderer.setPixelRatio(Math.min(devicePixelRatio || 1, touchOrSmall.matches ? 1.35 : 1.8));
  renderer.outputColorSpace = THREE.SRGBColorSpace;
  renderer.toneMapping = THREE.ACESFilmicToneMapping;
  renderer.toneMappingExposure = 1.55;
  const scene = new THREE.Scene();
  const camera = new THREE.PerspectiveCamera(35, 1, .1, 80);
  camera.position.set(0, 0, 10.8);
  const ambient = new THREE.AmbientLight(0xffffff, 1.6);
  scene.add(ambient);
  const keyLight = new THREE.DirectionalLight(0xf7e3b0, 4.5);
  keyLight.position.set(-3, 6, 6);scene.add(keyLight);
  const rimLight = new THREE.PointLight(0xb7a579, 120, 25);
  rimLight.position.set(3.8, -1, -2.5);scene.add(rimLight);
  const cold = new THREE.DirectionalLight(0xbdbbb2, 2.3);
  cold.position.set(4, -2, 3);scene.add(cold);

  const phoneRig = new THREE.Group();scene.add(phoneRig);
  const device = new THREE.Group(); phoneRig.add(device);
  const shellMat = new THREE.MeshPhysicalMaterial({ color: 0x242421, metalness: .86, roughness: .27, clearcoat: .8, clearcoatRoughness: .22 });
  const edgeMat = new THREE.MeshStandardMaterial({ color: 0x74694d, metalness: .97, roughness: .28 });
  const glassMat = new THREE.MeshPhysicalMaterial({ color: 0x0e1010, metalness: .28, roughness: .19, clearcoat: 1, clearcoatRoughness: .08 });
  const sandMat = new THREE.MeshStandardMaterial({ color: 0xd8c48a, metalness: .58, roughness: .3 });
  function roundedShape(w, h, r) {
    const x = -w / 2, y = -h / 2, s = new THREE.Shape();
    s.moveTo(x + r, y);s.lineTo(x + w - r, y);s.quadraticCurveTo(x + w, y, x + w, y + r);
    s.lineTo(x + w, y + h - r);s.quadraticCurveTo(x + w, y + h, x + w - r, y + h);
    s.lineTo(x + r, y + h);s.quadraticCurveTo(x, y + h, x, y + h - r);
    s.lineTo(x, y + r);s.quadraticCurveTo(x, y, x + r, y);return s;
  }
  function extruded(w,h,r,depth,material,z) {
    const geometry = new THREE.ExtrudeGeometry(roundedShape(w,h,r),{depth,bevelEnabled:true,bevelThickness:.025,bevelSize:.025,bevelSegments:3,curveSegments:8,steps:1});
    const mesh = new THREE.Mesh(geometry,material);mesh.position.z=z;return mesh;
  }
  device.add(extruded(2.15,4.37,.25,.22,edgeMat,-.13));
  device.add(extruded(2.09,4.31,.23,.21,shellMat,-.11));
  device.add(extruded(1.95,4.12,.19,.012,glassMat,.135));

  // Procedural display art, no remote textures or image downloads.
  const display = document.createElement('canvas');display.width=512;display.height=1024;
  const ctx=display.getContext('2d');
  ctx.fillStyle='#101110';ctx.fillRect(0,0,512,1024);
  ctx.strokeStyle='#252520';ctx.lineWidth=2;
  for(let n=0;n<22;n++){ctx.beginPath();ctx.moveTo(0,n*50);ctx.lineTo(512,n*50);ctx.stroke();}
  ctx.fillStyle='#d8c48a';ctx.fillRect(43,61,23,23);ctx.fillRect(67,61,23,23);ctx.fillRect(43,86,23,23);ctx.fillRect(91,86,23,23);
  ctx.font='bold 47px monospace';ctx.fillText('CLOUDIX',43,175);
  ctx.fillStyle='#aaa69c';ctx.font='17px monospace';ctx.fillText('YOUR STORAGE. YOUR RULES.',43,216);
  ctx.strokeStyle='#6c624b';ctx.strokeRect(43,267,426,225);
  ctx.font='16px monospace';ctx.fillStyle='#d8c48a';ctx.fillText('STORAGE / LOCAL',66,306);
  ctx.fillStyle='#26271f';ctx.fillRect(66,334,380,16);ctx.fillStyle='#d8c48a';ctx.fillRect(66,334,245,16);
  ctx.fillStyle='#aaa69c';ctx.font='14px monospace';ctx.fillText('DEVICE CONNECTED',66,399);ctx.fillText('NETWORK: PRIVATE',66,436);
  ctx.fillStyle='#d8c48a';ctx.fillRect(67,418,10,10);
  ctx.strokeStyle='#49463b';ctx.beginPath();ctx.moveTo(43,555);ctx.lineTo(469,555);ctx.stroke();
  ctx.font='15px monospace';ctx.fillStyle='#a09a85';ctx.fillText('FILES',43,598);
  [[638,'DOCUMENTS'],[707,'PHOTOS'],[776,'ARCHIVES']].forEach(([y,label])=>{ctx.strokeStyle='#514c3d';ctx.strokeRect(46,y-21,23,24);ctx.fillStyle='#eee3c1';ctx.font='19px monospace';ctx.fillText(label,89,y);ctx.fillStyle='#7a7568';ctx.fillText('↗',417,y);});
  ctx.fillStyle='#7d765f';ctx.font='14px monospace';ctx.fillText('PRIVATE BY DESIGN_',43,953);
  const texture=new THREE.CanvasTexture(display);texture.colorSpace=THREE.SRGBColorSpace;texture.anisotropy=Math.min(renderer.capabilities.getMaxAnisotropy(),8);
  const screen=new THREE.Mesh(new THREE.PlaneGeometry(1.91,4.04),new THREE.MeshBasicMaterial({map:texture}));screen.position.z=.177;device.add(screen);
  const speaker=new THREE.Mesh(new THREE.CapsuleGeometry(.045,.22,3,9),new THREE.MeshStandardMaterial({color:0x3d3c36,metalness:.8,roughness:.3}));speaker.rotation.z=Math.PI/2;speaker.position.set(0,2.06,.2);device.add(speaker);
  const lens=new THREE.Mesh(new THREE.CircleGeometry(.043,24),new THREE.MeshBasicMaterial({color:0x706951}));lens.position.set(.23,2.06,.204);device.add(lens);
  // Hardware side buttons.
  for(const [y,h] of [[.92,.35],[.44,.28]]){const button=new THREE.Mesh(new THREE.BoxGeometry(.042,h,.1),edgeMat);button.position.set(1.12,y,.01);device.add(button);}
  // Sculptural floating storage cells.
  const blocks=new THREE.Group();phoneRig.add(blocks);
  const cubeEdges=new THREE.EdgesGeometry(new THREE.BoxGeometry(1,1,1));
  const blockMat=new THREE.MeshPhysicalMaterial({color:0x635a40,metalness:.89,roughness:.3});
  const positions=[[-1.9,1.48,-.65,.34],[-2.12,.78,.3,.14],[1.9,1.75,-.9,.21],[2.14,.5,-.4,.48],[-1.92,-1.35,.05,.29],[1.83,-1.51,.25,.23],[2.35,-.87,-1.1,.13],[-1.68,-2.05,-.5,.12]];
  positions.forEach(([x,y,z,s],i)=>{const mesh=new THREE.Mesh(new THREE.BoxGeometry(s,s,s),i%3===0?sandMat:blockMat);mesh.position.set(x,y,z);mesh.rotation.set(i*.39,i*.54,i*.22);blocks.add(mesh);if(i%2===0){const edges=new THREE.LineSegments(cubeEdges,new THREE.LineBasicMaterial({color:0xd8c48a,transparent:true,opacity:.36}));edges.position.set(x,y,z);edges.scale.setScalar(s*1.4);blocks.add(edges);}});
  const points=[];for(let i=0;i<46;i++){const angle=i*2.399,dist=2.0+(i%8)*.18;points.push(Math.cos(angle)*dist,Math.sin(angle)*dist*.75,(i%9)*.18-1.0);}
  const pointGeom=new THREE.BufferGeometry();pointGeom.setAttribute('position',new THREE.Float32BufferAttribute(points,3));
  const dust=new THREE.Points(pointGeom,new THREE.PointsMaterial({color:0xd8c48a,size:.025,transparent:true,opacity:.46,sizeAttenuation:true}));phoneRig.add(dust);
  device.rotation.set(.13,-.27,-.15);
  const clock=new THREE.Clock();let visible=true,disposed=false, targetX=0,targetY=0,last=0;
  const pointerHandler=e=>{const b=art.getBoundingClientRect();targetX=((e.clientX-b.left)/b.width-.5);targetY=((e.clientY-b.top)/b.height-.5);};
  if(!reducedMotion.matches && !touchOrSmall.matches)art.addEventListener('pointermove',pointerHandler,{passive:true});
  function resize(){const w=Math.max(1,art.clientWidth),h=Math.max(1,art.clientHeight);camera.aspect=w/h;camera.position.z=touchOrSmall.matches?11.7:10.2;camera.updateProjectionMatrix();renderer.setSize(w,h,false);}
  const ro=new ResizeObserver(resize);ro.observe(art);resize();
  const io=new IntersectionObserver(entries=>{visible=entries[0]?.isIntersecting||false;},{threshold:0});io.observe(art);
  art.classList.add('webgl-ready');
  function loop(now){if(disposed)return;requestAnimationFrame(loop);if(!visible||document.hidden)return;
    const mobile=touchOrSmall.matches;if(mobile&&now-last<33)return;last=now;
    const t=clock.getElapsedTime();
    if(!reducedMotion.matches){
      const scrollRatio=Math.max(0,Math.min(1,window.scrollY/(innerHeight||1)));
      phoneRig.rotation.y+=(targetX*.18+scrollRatio*.27-phoneRig.rotation.y)*.035;
      phoneRig.rotation.x+=(-targetY*.1+scrollRatio*.07-phoneRig.rotation.x)*.035;
      device.rotation.y=-.27+Math.sin(t*.24)*.14;
      device.rotation.z=-.15+Math.sin(t*.31)*.025;
      device.position.y=Math.sin(t*.7)*.11;
      blocks.rotation.y=t*.075;dust.rotation.z=t*.03;
    }
    renderer.render(scene,camera);
  }
  renderer.render(scene,camera);requestAnimationFrame(loop);
  addEventListener('pagehide',()=>{disposed=true;ro.disconnect();io.disconnect();renderer.dispose();texture.dispose();},{once:true});
}