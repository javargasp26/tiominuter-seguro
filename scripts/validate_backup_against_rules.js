#!/usr/bin/env node
// Checks that every row of a phone backup, mapped to Firestore as docs/firestore-model.md says,
// is accepted by firebase/firestore.rules. Prints counts and field names only.
//
// Usage: node scripts/validate_backup_against_rules.js <backup.tar>
// The tar must contain databases/tiominuter.db and databases/programs.db (+ -wal/-shm). Only
// databases/ is extracted, into a temp dir outside the repo that is deleted at the end. Starts
// the Firestore emulator with `firebase emulators:exec` (like scripts/test_rules.sh) and exits
// with 1 when any doc is rejected. Needs Node 22.13+ (node:sqlite), Java 21 and firebase-tools.
import { execFileSync } from 'node:child_process';
import { existsSync } from 'node:fs';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const repo = resolve(dirname(fileURLToPath(import.meta.url)), '..');
const testsDir = resolve(repo, 'firebase/rules-tests');

if (process.env.FIRESTORE_EMULATOR_HOST) {
  // Inside emulators:exec: the tar path comes through the environment (no shell quoting)
  const { validateBackup } = await import('../firebase/rules-tests/backup/validateBackup.js');
  const rejected = await validateBackup(process.env.TIOMINUTER_BACKUP_TAR);
  console.log(rejected === 0 ? '\nAll docs accepted.' : `\n${rejected} docs rejected.`);
  process.exit(rejected === 0 ? 0 : 1);
}

const tar = process.argv[2];
if (!tar || !existsSync(tar)) {
  console.error('Usage: node scripts/validate_backup_against_rules.js <backup.tar>');
  process.exit(2);
}
if (!existsSync(resolve(testsDir, 'node_modules'))) {
  execFileSync('npm', ['--prefix', testsDir, 'ci'], { stdio: 'inherit' });
}
try {
  execFileSync(
    'firebase',
    ['emulators:exec', '--only', 'firestore', '--project', 'tiominuter-e43f7', `node "${fileURLToPath(import.meta.url)}"`],
    { cwd: repo, stdio: 'inherit', env: { ...process.env, TIOMINUTER_BACKUP_TAR: resolve(tar) } },
  );
} catch {
  process.exit(1);
}
