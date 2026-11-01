const fs = require("fs");
const d = JSON.parse(
  fs.readFileSync(
    "C:/Users/danka/.cursor/projects/c-Users-danka-Documents-Skipii/agent-tools/32a0072c-b646-4970-b64c-799f661cd644.txt",
    "utf8",
  ),
);
for (const t of d.tree) {
  if (t.type !== "blob") continue;
  const p = t.path;
  if (
    /backdrop|LiquidGlass|GlassNav|FloatingBottom|AppSettings|SettingsScreen|Appearance|haze/i.test(p) &&
    (p.endsWith(".kt") || p.endsWith(".kts"))
  ) {
    console.log(p);
  }
}
