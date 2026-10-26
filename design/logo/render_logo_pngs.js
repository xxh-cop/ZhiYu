const fs = require('fs');
const path = require('path');
const { Resvg } = require('@resvg/resvg-js');

const SRC = __dirname;
const OUT = __dirname + '/png';
fs.mkdirSync(OUT, { recursive: true });

const jobs = [
  ['zhiyu-seal-student.svg', 'zhiyu-seal-student-1024.png', 1024],
  ['zhiyu-seal-student.svg', 'zhiyu-seal-student-512.png', 512],
  ['zhiyu-seal-student.svg', 'zhiyu-seal-student-48.png', 48],
  ['zhiyu-seal-student.svg', 'zhiyu-seal-student-32.png', 32],
  ['zhiyu-seal-admin.svg', 'zhiyu-seal-admin-512.png', 512],
  ['zhiyu-seal-inverse-student.svg', 'zhiyu-seal-inverse-student-512.png', 512],
  ['zhiyu-seal-inverse-admin.svg', 'zhiyu-seal-inverse-admin-512.png', 512],
  ['zhiyu-logo-horizontal-student.svg', 'zhiyu-logo-horizontal-student-2x.png', 524],
  ['zhiyu-logo-horizontal-admin.svg', 'zhiyu-logo-horizontal-admin-2x.png', 524],
];

for (const [src, dst, width] of jobs) {
  const svg = fs.readFileSync(path.join(SRC, src), 'utf8');
  const png = new Resvg(svg, { fitTo: { mode: 'width', value: width } }).render().asPng();
  fs.writeFileSync(path.join(OUT, dst), png);
  console.log(dst, (png.length / 1024).toFixed(1) + 'KB');
}
