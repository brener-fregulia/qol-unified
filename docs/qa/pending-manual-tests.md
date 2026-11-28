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

## Completed

None yet.
