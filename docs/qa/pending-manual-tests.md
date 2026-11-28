# Pending Manual QA

The backlog of manual tests the PO has not run yet, for every Unified module.
Each case points to its specification for details, and nothing is repeated
here. Automated results (unit and game tests) are recorded in the specs and
are **not** manual QA.

## Conventions

- **Stable IDs.** `<FEATURE>-NN` (for example `FQ-03`). An ID is never reused
  or renumbered, even after a case is closed or dropped.
- **Status:** `Pending`, `Passed`, `Failed` or `Blocked`. The checkbox is
  ticked only when the status is `Passed`.
- **Only confirmed manual results count.** A case becomes `Passed` only when
  the PO (or a tester they name) reports it. Agents never infer a manual
  result from automated tests, and never mark a case `Passed` themselves.
- **Failed** cases record the steps, the build (module version and commit),
  the platform and the relevant `latest.log` lines.
- **Blocked** cases name the blocker (for example "no public-IP server").
- **New work adds cases.** When a feature has steps that only a person can
  check, add them here in the same change as the feature, under its module.
- **Closing.** Move a finished case to [Completed](#completed), with the date
  and the evidence. Never delete a case silently; a dropped case also moves
  there, with the reason.
- **Releases.** A feature with `Pending`, `Failed` or `Blocked` cases is never
  described as fully validated in a changelog or release note.
- **Safety.** Use disposable worlds and dedicated test instances only. Never
  test failure paths on the PO's real worlds or instances.

Case template:

```markdown
- [ ] **ID — Title** · Priority · Status: Pending
  - Preconditions:
  - Steps:
  - Expected:
  - Observations / evidence:
```

## Summary

| Module | Feature | Priority | Pending | Spec |
| --- | --- | --- | --- | --- |
| Optimizations | FastQuit (`fast_quit`) | High | 11 | [fastquit-v1](../optimizations/specifications/fastquit-v1.md) §12 |
| Optimizations | Fast IP Ping (`fast_ip_ping`) | Medium | 7 | [fast-ip-ping-v1](../optimizations/specifications/fast-ip-ping-v1.md) §15 |
| QoL | Target Info, placed Shulker Box | Medium | 4 | [target-info-v1](../qol/specifications/target-info-v1.md) §17 |
| QoL | Enhanced Statistics (`enhanced_statistics`, not implemented) | Medium | 6 | [enhanced-statistics-v1](../qol/specifications/enhanced-statistics-v1.md) §11 |
| QoL | Inventory Search (`inventory_search`, implemented 2026-11-28) | Medium | 8 | [inventory-search-v1](../qol/specifications/inventory-search-v1.md) §11 |

Common preconditions, unless a case says otherwise: production JARs from
`./gradlew build`, a dedicated Prism test instance with Fabric API and Fabric
Language Kotlin ([setup](../development/linux-setup.md#4-testing-a-build-in-prism-launcher)),
on CachyOS and, where the spec asks for it, Windows 11. Record the module
version and commit with every result.

## Unified: Optimizations

### FastQuit (`fast_quit`) — High priority

Default **off** until manual QA is complete (Q-FQ1). Disposable singleplayer
worlds only. The strings to look for are "Saving world in the background",
"World saved" and "Finishing saving the previous world...".

- [ ] **FQ-01 — Normal Save and Quit** · High · Status: Pending
  - Preconditions: a new disposable world; FastQuit on.
  - Steps: place and break blocks, change the inventory, fill a chest;
    Save and Quit.
  - Expected: the title screen appears without errors; "World saved" follows.
  - Observations / evidence:

- [ ] **FQ-02 — Immediate reopening** · High · Status: Pending
  - Preconditions: FQ-01 world; FastQuit on.
  - Steps: make a change, Save and Quit, and immediately open the same world.
  - Expected: "Finishing saving the previous world..." shows until the save
    ends, then the world opens; no session-lock error; the change is present.
  - Observations / evidence:

- [ ] **FQ-03 — Open another world** · High · Status: Pending
  - Preconditions: two disposable worlds; FastQuit on.
  - Steps: leave world A and immediately open world B.
  - Expected: B opens only after A's save finished (waiting message); both
    worlds keep their changes.
  - Observations / evidence:

- [ ] **FQ-04 — Exit the game during a background save** · High · Status: Pending
  - Preconditions: a world with explored terrain; FastQuit on.
  - Steps: leave the world and immediately press Quit Game. Repeat, closing
    the window instead.
  - Expected: the game waits for the save, then exits. After restarting, the
    changes are present and `latest.log` has no "Failed to unlock".
  - Observations / evidence:

- [ ] **FQ-05 — Data persistence** · High · Status: Pending
  - Preconditions: after FQ-01 to FQ-04.
  - Steps: reopen each world and check the player position, inventory,
    placed and broken blocks, entities (a named or penned animal) and chest
    contents.
  - Expected: everything matches the state before leaving.
  - Observations / evidence:

- [ ] **FQ-06 — Vanilla comparison** · High · Status: Pending
  - Preconditions: FastQuit off.
  - Steps: Save and Quit; reopen.
  - Expected: vanilla behavior, with the "Saving world" screen until the save
    ends and no FastQuit toast.
  - Observations / evidence:

- [ ] **FQ-07 — Multiplayer** · High · Status: Pending
  - Preconditions: FastQuit on; the PO's Paper test server.
  - Steps: join, then disconnect.
  - Expected: disconnecting is unchanged (no FastQuit toast, nothing waits).
  - Observations / evidence:

- [ ] **FQ-08 — Other world operations while saving** · High · Status: Pending
  - Preconditions: two disposable worlds; FastQuit on.
  - Steps: leave world A and immediately, on world B, do each of: create a
    new world, Edit, Edit → Make Backup, Edit → Optimize World, Delete.
    Repeat the leave before each.
  - Expected: each operation waits for A's save first; no error and no
    concurrent access to a world folder (Q-FQ2).
  - Observations / evidence:

- [ ] **FQ-09 — Logs and errors** · High · Status: Pending
  - Preconditions: after FQ-01 to FQ-08.
  - Steps: read `latest.log` from each session.
  - Expected: no save failure, session-lock error, crash, or shutdown work
    abandoned at exit.
  - Observations / evidence:

- [ ] **FQ-10 — Open to LAN** · Medium · Status: Pending
  - Preconditions: FastQuit on; a second client joined over LAN.
  - Steps: the host does Save and Quit.
  - Expected: the guest is disconnected as in vanilla; the world saves.
  - Observations / evidence:

- [ ] **FQ-11 — Conflict with the original FastQuit** · Medium · Status: Pending
  - Preconditions: the `fastquit` mod installed alongside.
  - Steps: open Unified: Optimizations' settings.
  - Expected: `fast_quit` is shown inactive, naming `fastquit` as the reason.
  - Observations / evidence:

### Fast IP Ping (`fast_ip_ping`) — Medium priority

Default **on** for non-public IP literals (Q-FIP8). A faster ping is **not**
acceptance evidence: the feature removes a reverse DNS lookup, not network
latency. Never try to bypass Mojang's server blocklist.

- [ ] **FIP-01 — Tailscale IPv4** · Medium · Status: Pending
  - Preconditions: the Paper server's Tailscale `100.x.y.z` address.
  - Steps: add it to the server list; refresh.
  - Expected: status, player count and ping are shown.
  - Observations / evidence:

- [ ] **FIP-02 — MagicDNS hostname** · Medium · Status: Pending
  - Preconditions: the server's Tailscale MagicDNS name.
  - Steps: add it; refresh; join.
  - Expected: resolves and connects normally (host names use the vanilla
    path).
  - Observations / evidence:

- [ ] **FIP-03 — LAN IPv4** · Medium · Status: Pending
  - Preconditions: the server's private LAN address (`192.168.x.y` or
    similar).
  - Steps: add it; refresh.
  - Expected: status is shown.
  - Observations / evidence:

- [ ] **FIP-04 — Feature toggle** · Medium · Status: Pending
  - Steps: with FIP-01 and FIP-03 listed, turn Fast IP Ping off and refresh;
    turn it on and refresh.
  - Expected: status is shown in both states; the change applies without a
    restart.
  - Observations / evidence:

- [ ] **FIP-05 — Joining is unaffected** · Medium · Status: Pending
  - Steps: join the server by IP and by host name, and via Direct Connect.
  - Expected: joining and playing behave as without the mod.
  - Observations / evidence:

- [ ] **FIP-06 — Public-IP fallback** · Low · Status: Pending
  - Preconditions: an accessible server on a public IP, if one is available
    (otherwise mark Blocked).
  - Steps: add it by IP; refresh.
  - Expected: status works through the vanilla path (Q-FIP3).
  - Observations / evidence:

- [ ] **FIP-07 — Invalid or unreachable address** · Medium · Status: Pending
  - Steps: add `999.1.1.1`, a private IP with no server, and a closed port.
  - Expected: vanilla's usual connection or resolve error for each; no hang,
    no crash.
  - Observations / evidence:

## Unified: QoL

### Target Info: placed Shulker Box — Medium priority

**Observed by the PO, preliminary (CachyOS, Paper server, 2026-11-27):**
contents are unknown until the box is opened; after opening, the cached
representative item icon is shown; the enhancement appears to work. This is
**not** full manual QA. The automated coverage is in the spec, §17.4.

- [ ] **SH-01 — Contents update** · Medium · Status: Pending
  - Steps: open a box, change its contents (a different first or
    predominant item), close it.
  - Expected: the icon updates to the new representative item.
  - Observations / evidence:

- [ ] **SH-02 — Block replacement** · Medium · Status: Pending
  - Steps: open and close a box; break it and place another (or the same) box
    at the same position.
  - Expected: the box is unknown again (no icon) until opened.
  - Observations / evidence:

- [ ] **SH-03 — Session reset** · Medium · Status: Pending
  - Steps: open and close a box; disconnect and reconnect.
  - Expected: the box is unknown until opened again.
  - Observations / evidence:

- [ ] **SH-04 — Stale contents** · Low · Status: Pending
  - Preconditions: a second player or a hopper feeding the box.
  - Steps: open and close the box; have its contents change without opening
    it.
  - Expected: the overlay still says "Contents as last seen" and never claims
    the contents are current.
  - Observations / evidence:

### Enhanced Statistics (`enhanced_statistics`) — Medium priority

Specified, **not implemented**: these cases run once the feature exists. A
disposable world, or the PO's Paper test server.

- [ ] **ES-01 — Vanilla parity and switch** · Medium · Status: Pending
  - Steps: open Statistics with the feature off, then on.
  - Expected: off, vanilla's screen; on, the same tabs, title and Done button
    plus search and filters; the values match between the two.
  - Observations / evidence:

- [ ] **ES-02 — Paper server** · Medium · Status: Pending
  - Preconditions: the Paper test server; some play on it.
  - Steps: open Statistics; search, sort and filter; reopen.
  - Expected: values equal those on vanilla's screen; no error or kick; a
    single "Retrieving statistics..." per opening.
  - Observations / evidence:

- [ ] **ES-03 — Long-lived player** · Medium · Status: Pending
  - Preconditions: a player with many hours and hundreds of item rows.
  - Steps: open each tab; sort Items by every column; search; check the
    totals.
  - Expected: no visible delay; totals plausible against the rows; deterministic
    order.
  - Observations / evidence:

- [ ] **ES-04 — Portuguese** · Medium · Status: Pending
  - Steps: game language pt_br; search with and without accents (for example
    "distancia", "Distância").
  - Expected: both find the same rows; every new label is translated.
  - Observations / evidence:

- [ ] **ES-05 — Keyboard, narrator, small window** · Medium · Status: Pending
  - Steps: navigate only with the keyboard; enable the narrator; repeat at
    GUI scale 4 in a 1280×720 window.
  - Expected: every control is reachable and narrated; nothing overlaps.
  - Observations / evidence:

- [ ] **ES-06 — Modded statistics** · Low · Status: Pending
  - Preconditions: a mod that registers custom statistics (if one is available;
    otherwise Blocked).
  - Steps: open General; search for the mod's statistic.
  - Expected: shown in the Other group with the mod's formatting.
  - Observations / evidence:

### Inventory Search (`inventory_search`) — Medium priority

**Implemented 2026-11-28** ([spec §15](../qol/specifications/inventory-search-v1.md#15-v1-implementation-implemented-2026-11-28)).
The automated game test covers these paths; it does not replace them. Use
production JARs with Fabric API and Fabric Language Kotlin, in disposable
worlds only.

- [ ] **IS-01 — Basic search** · Medium · Status: Pending
  - Steps: in a double chest with mixed items, press Ctrl+F and type a name,
    an id (`minecraft:diamond`), an enchantment (`mending`, also on an
    enchanted book) and two words (`diamond sword`); then clear the field.
  - Expected: matches get a gold frame, other items are dimmed but still
    recognizable, empty slots are unchanged; the count inside the field is
    right (red at 0); clearing restores the vanilla look.
  - Observations / evidence:

- [ ] **IS-02 — Keys while typing** · High · Status: Pending
  - Steps: with the field focused, type `e`, `1`–`9`, `f`, `q` while hovering
    an item; press Backspace; press Escape, then Escape again. Reopen the
    container.
  - Expected: the keys only type; nothing moves, drops or closes; Backspace
    edits; the first Escape unfocuses, the second closes; the field is empty
    after reopening.
  - Observations / evidence:

- [ ] **IS-03 — Clicks unchanged** · High · Status: Pending
  - Steps: with an active query: click, Shift-click, Shift-double-click,
    drag-spread, and click the field while carrying an item.
  - Expected: every action behaves as without the feature; nothing is dropped
    by clicking the field.
  - Observations / evidence:

- [ ] **IS-04 — Screens and placement** · Medium · Status: Pending
  - Steps: open the player inventory (recipe book closed and open), a chest, a
    double chest, a barrel, an Ender Chest, a Shulker Box and a hopper, at
    1920×1080 and 1280×720 and at every GUI scale offered; also a double chest
    renamed with a long name.
  - Expected: the field never covers the title, a slot or the recipe book; with
    a long name at a small size a search icon appears instead, and Ctrl+F or a
    click opens it.
  - Observations / evidence:

- [ ] **IS-05 — Inventory Mouse Tweaks** · High · Status: Pending
  - Steps: with an active query, use every Mouse Tweaks gesture.
  - Expected: gestures work exactly as without a query.
  - Observations / evidence:

- [ ] **IS-06 — Live contents and Paper** · Medium · Status: Pending
  - Preconditions: the Paper test server; a hopper feeding a chest, or a
    second player.
  - Steps: keep a query active while the contents change; also open a plugin
    menu, if one is available.
  - Expected: the highlight follows the change within a moment; no kick or
    error; plugin menus are only highlighted.
  - Observations / evidence:

- [ ] **IS-07 — Recipe viewers** · Low · Status: Pending
  - Preconditions: JEI or REI for 26.3 installed (if available; otherwise
    Blocked).
  - Steps: open the inventory and a chest; press Ctrl+F; click the Inventory
    Search field and type.
  - Expected: no overlap with their panels or search field; Ctrl+F goes to
    the recipe viewer, not Inventory Search; both searches usable.
  - Observations / evidence:

- [ ] **IS-08 — Portuguese and narrator** · Low · Status: Pending
  - Steps: pt_br; search "picareta", "eficiencia" and "afiacao"; enable the
    narrator and focus the field.
  - Expected: accent-insensitive matches; the hint, value and count are
    narrated.
  - Observations / evidence:

## Decisions pending QA

These stay open until the PO decides; QA results inform them but do not
resolve them.

| ID | Status | Question | Depends on |
| --- | --- | --- | --- |
| Q-FQ1 | [Open] | Turn FastQuit on by default after manual QA passes? | FQ-01 to FQ-11 |
| Q-FQ2 | [Proposed] | Keep the rule that every world access waits for a pending save (not only the same world)? | FQ-03, FQ-08 |
| Q-FQ3 | [Proposed] | Keep FastQuit's settings to the on/off switch? | — |
| Q-FIP8 | [Open] | Confirm Fast IP Ping on by default for non-public IPs. | FIP-01 to FIP-07 |
| Q-FIP3 | [Open] | Public IPs stay on the vanilla path: optimizing them would skip the blocklist check of their PTR name. | FIP-06 |
| Q-ES9 | [Open] | Enhanced Statistics on by default? | ES-01 to ES-06 |
| Q-IS9 | [Approved 2026-11-28] | Inventory Search on by default: yes; QA confirms it | IS-01 to IS-08 |

## Completed

None yet.
