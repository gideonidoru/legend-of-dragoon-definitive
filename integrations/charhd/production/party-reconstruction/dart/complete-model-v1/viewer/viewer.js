// AGPL v3. Three.js is separately licensed under MIT; see vendor/THREE-LICENSE.txt.
import * as THREE from 'three';
import {OrbitControls} from './vendor/controls/OrbitControls.js';
import {GLTFLoader} from './vendor/loaders/GLTFLoader.js';

const status = document.querySelector('#status');
const scene = new THREE.Scene();
scene.background = new THREE.Color(0x15181d);
const camera = new THREE.PerspectiveCamera(35, innerWidth / innerHeight, .01, 30);
const renderer = new THREE.WebGLRenderer({antialias: true, preserveDrawingBuffer: true});
renderer.setPixelRatio(Math.min(devicePixelRatio, 2));
renderer.setSize(innerWidth, innerHeight);
renderer.outputColorSpace = THREE.SRGBColorSpace;
renderer.toneMapping = THREE.ACESFilmicToneMapping;
renderer.toneMappingExposure = 1.1;
renderer.shadowMap.enabled = true;
renderer.shadowMap.type = THREE.PCFSoftShadowMap;
document.body.prepend(renderer.domElement);

const controls = new OrbitControls(camera, renderer.domElement);
controls.target.set(0, .94, 0);
controls.enableDamping = true;
controls.minDistance = .6;
controls.maxDistance = 14;
controls.maxPolarAngle = Math.PI * .88;
scene.add(new THREE.HemisphereLight(0xdde5ff, 0x56504a, 2.2));
function addLight(color, intensity, position) {
  const light = new THREE.DirectionalLight(color, intensity);
  light.position.set(...position);
  scene.add(light);
  return light;
}
const key = addLight(0xffead4, 3.1, [-2, 3, 4]);
key.castShadow = true;
key.shadow.mapSize.set(2048, 2048);
Object.assign(key.shadow.camera, {left: -2, right: 2, top: 3, bottom: -1});
key.shadow.normalBias = .008;
addLight(0xd3e3ff, 1.3, [3, 2, -2]);
addLight(0xffffff, .7, [2, 1, 3]);
const ground = new THREE.Mesh(new THREE.PlaneGeometry(40, 40),
  new THREE.MeshStandardMaterial({color: 0x15181d, roughness: 1}));
ground.rotation.x = -Math.PI / 2;
ground.position.y = -.01;
ground.receiveShadow = true;
scene.add(ground);

let actor, sequence = 0, mode = 'colour', wire = false;
const loader = new GLTFLoader();
const cache = new Map();
const poses = new Set(['neutral', 'relaxed', 'ready', 'guard', 'stride', 'before']);

function view(yaw = .45, elevation = .08) {
  if (!actor) return;
  const bounds = new THREE.Box3().setFromObject(actor);
  const size = bounds.getSize(new THREE.Vector3());
  const center = bounds.getCenter(new THREE.Vector3());
  const verticalFov = THREE.MathUtils.degToRad(camera.fov / 2);
  const horizontalFov = Math.atan(Math.tan(verticalFov) * camera.aspect);
  const usableHeight = Math.max(200, innerHeight - (innerWidth < 700 ? 340 : 200));
  const projectedWidth = Math.abs(size.x * Math.cos(yaw)) + Math.abs(size.z * Math.sin(yaw));
  const distance = (Math.max(size.y / (2 * Math.tan(verticalFov)) * innerHeight / usableHeight,
    projectedWidth / (2 * Math.tan(horizontalFov))) + size.z / 2) * 1.08;
  controls.target.copy(center);
  camera.position.copy(center).add(new THREE.Vector3(
    Math.sin(yaw) * distance, elevation * distance, Math.cos(yaw) * distance));
  controls.update();
}

function materialMode() {
  if (!actor) return;
  actor.traverse(object => {
    if (!object.isMesh) return;
    object.material = mode === 'clay' ? object.userData.clay : object.userData.original;
    object.material.wireframe = wire;
  });
  for (const name of ['colour', 'clay']) {
    document.getElementById(name).setAttribute('aria-pressed', String(mode === name));
  }
  document.getElementById('wire').setAttribute('aria-pressed', String(wire));
}

async function loadPose(pose) {
  if (!poses.has(pose)) throw new Error('Unknown review pose');
  const request = ++sequence;
  status.hidden = false;
  status.textContent = 'Loading ' + (pose === 'before' ? 'earlier sculpt' : pose + ' pose') + '…';
  status.classList.remove('error');
  try {
    let loaded = cache.get(pose);
    if (!loaded) {
      loaded = (await loader.loadAsync(pose + '.glb')).scene;
      loaded.traverse(object => {
        if (!object.isMesh) return;
        object.castShadow = true;
        object.receiveShadow = true;
        object.userData.original = object.material;
        object.userData.clay = new THREE.MeshStandardMaterial({color: 0xbdb8ad, roughness: .8});
      });
      cache.set(pose, loaded);
    }
    if (request !== sequence) return;
    if (actor) scene.remove(actor);
    actor = loaded;
    scene.add(actor);
    materialMode();
    document.getElementById('pose').value = pose;
    document.getElementById('download').href = pose + '.glb';
    let triangles = 0;
    actor.traverse(object => {
      if (object.isMesh) triangles += (object.geometry.index?.count || object.geometry.attributes.position.count) / 3;
    });
    document.getElementById('stats').textContent = Math.round(triangles).toLocaleString() + ' triangles · '
      + (pose === 'before' ? 'earlier unrigged sculpt' : 'full actor review');
    status.hidden = true;
    window.dartReview = {loaded: true, pose, triangles, view, setPose: loadPose,
      setMode(value) {mode = value; materialMode();}, scene, renderer, camera, controls};
    view();
  } catch (error) {
    if (request !== sequence) return;
    status.textContent = 'The model could not load. ' + error.message;
    status.classList.add('error');
    window.dartReviewError = String(error);
  }
}

document.getElementById('pose').addEventListener('change', event => loadPose(event.target.value));
document.getElementById('colour').onclick = () => {mode = 'colour'; materialMode();};
document.getElementById('clay').onclick = () => {mode = 'clay'; materialMode();};
document.getElementById('wire').onclick = () => {wire = !wire; materialMode();};
document.getElementById('turn').onclick = event => {
  controls.autoRotate = !controls.autoRotate;
  event.target.setAttribute('aria-pressed', String(controls.autoRotate));
};
document.getElementById('reset').onclick = () => view();
addEventListener('resize', () => {
  camera.aspect = innerWidth / innerHeight;
  camera.updateProjectionMatrix();
  renderer.setSize(innerWidth, innerHeight);
});
loadPose('ready');
renderer.setAnimationLoop(() => {controls.update(); renderer.render(scene, camera);});
