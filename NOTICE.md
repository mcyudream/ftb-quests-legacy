# Sources and notices

FTB Quests, FTB Library and FTB Utilities retain the original authors' notices and NovaEngineering provenance. This repository does not replace their original copyright or assert a new blanket license over upstream code.

| Module | Source baseline |
| --- | --- |
| FTB Quests | [NovaEngineering-Source/FTB-Quests-NovaEng](https://github.com/NovaEngineering-Source/FTB-Quests-NovaEng), commit `00eca86f04f843d2e779c70a19281a2760f3ebce` |
| FTB Library | [NovaEngineering-Source/FTB-Library-NovaEng](https://github.com/NovaEngineering-Source/FTB-Library-NovaEng), commit `86c01bbb2610c3c2107c1269c31167ab2d3d4120` |
| FTB Utilities | [NovaEngineering-Source/FTB-Utilities-NovaEng](https://github.com/NovaEngineering-Source/FTB-Utilities-NovaEng), commit `e5dc0bdd4cfb1f9ff5ea2377287a61bb7c5f6540` |
| Modern feature reference | [FTBTeam/FTB-Quests](https://github.com/FTBTeam/FTB-Quests), commit `3fc875bb085d4b2236b98abc042c5fa40711b67d` |

Original authors include LatvianModder / FTB contributors and NovaEngineering contributors. Cleanroom maintenance and additions in this repository: yudream. See the module `upstream-lock.json` files and original source comments for attribution.

`media-api/` is a **compile-only signature SDK**, containing yudream's Media API public signatures; its source license is preserved in `media-api/LICENSE`. `mcp-server/` contains only yudream's build-time API classes. Neither SDK is a playable Mod or included in the three production JARs.

WATERMeDIA, VLC, native runtime binaries, ItemFilters and the optional Mod dependency JARs are not committed or redistributed by this source repository or its release workflow. Media API's separately installed runtime remains optional. Optional compile inputs are fetched from their original/public distributions and hash checked by `tools/bootstrap.py`.
