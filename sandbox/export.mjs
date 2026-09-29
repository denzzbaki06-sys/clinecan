// Trusted exporter. Output is data, never extracted as executable files on the host.
import { readdirSync, lstatSync, readFileSync } from 'node:fs'
const files = []; let total = 0
function walk(dir, prefix = '') {
  for (const name of readdirSync(dir).sort()) {
    if (!/^[A-Za-z0-9_][A-Za-z0-9_.-]*$/.test(name) || name.includes('..')) throw Error('Unsafe artifact path')
    const path = `${dir}/${name}`, rel = prefix + name, stat = lstatSync(path)
    if (stat.isSymbolicLink()) throw Error('Artifact symlink')
    if (stat.isDirectory()) { if (rel.length > 180) throw Error('Artifact depth'); walk(path, rel + '/'); }
    else {
      if (!stat.isFile() || stat.size > 1000000 || (total += stat.size) > 2000000 || files.length >= 64) throw Error('Artifact limit')
      files.push({ path: rel, content: readFileSync(path).toString('base64') })
    }
  }
}
walk('/workspace/dist')
if (!files.some(f => f.path === 'index.html')) throw Error('Missing entry')
process.stdout.write(JSON.stringify({ files }))
