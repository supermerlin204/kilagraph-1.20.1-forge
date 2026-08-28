# KilaGraph

<div align="center">

**An unofficial Minecraft 1.20.1 Forge port of the KilaGraph node graph and shader graph toolkit.**

[![Minecraft](https://img.shields.io/badge/Minecraft-1.20.1-62B47A?style=for-the-badge)](https://www.minecraft.net/)
[![Forge](https://img.shields.io/badge/Forge-47.4.23-E04E14?style=for-the-badge)](https://files.minecraftforge.net/)
[![LDLib2](https://img.shields.io/badge/Built%20on-LDLib2-2F80ED?style=for-the-badge)](https://github.com/Low-Drag-MC/LDLib2)
[![License](https://img.shields.io/badge/License-MIT-green?style=for-the-badge)](LICENSE)

</div>

## Unofficial Forge Port / 非官方 Forge 移植

> [!IMPORTANT]
> This repository is an **unofficial Minecraft 1.20.1 Forge port** of the original
> [KilaGraph](https://github.com/Low-Drag-MC/KilaGraph) project. It is not an official release
> from, or endorsed by, the upstream maintainers.
>
> 本仓库是原版 [KilaGraph](https://github.com/Low-Drag-MC/KilaGraph) 的**非官方 Minecraft
> 1.20.1 Forge 移植版**，并非上游维护者发布或认可的官方版本。

The port keeps the original package structure and features where Forge 1.20.1 provides an
equivalent API. Compatibility code is used for APIs that only exist in newer Minecraft or NeoForge
versions. Please report problems specific to this port to the maintainer of this repository rather
than to the upstream project.

本移植版尽可能保留原项目的包结构与功能，并对新版 Minecraft 或 NeoForge 独有 API 提供 Forge
1.20.1 兼容实现。请勿将本移植版特有的问题提交到原项目的问题追踪器。

KilaGraph builds on LDLib2's Node Graph Toolkit to provide in-game programmable graphs for mods. It is aimed at authors who want visual logic graphs, reusable graph resources, and ShaderGraph-style authoring for custom RenderTypes without writing every shader and editor workflow by hand.

## Features

- **Blueprint Graphs**: general-purpose programmable node graphs for mod-side logic and data flow.
- **RenderType Graphs**: visual shader graphs that compile into Minecraft RenderType pipelines.
- **Shader Function Graphs**: reusable shader subgraphs that can be shared across RenderType graphs.
- **Editor integration**: LDLib2-powered graph editing, resource management, node libraries, settings panels, and live shader previews.
- **Minecraft-aware nodes**: utilities for items, blocks, fluids, entities, NBT, world queries, math, lists, maps, strings, and shader operations.

## Requirements

- Minecraft `1.20.1`
- Forge `47.4.23`
- LDLib2 `2.2.27+forge.1.20.1`
- Kotlin for Forge `4.11.0+` (required by LDLib2)

Exact dependency ranges are defined in `gradle.properties`.

## Links

- [LDLib2](https://github.com/Low-Drag-MC/LDLib2)
- [Original KilaGraph repository / 原项目仓库](https://github.com/Low-Drag-MC/KilaGraph)

## License

KilaGraph is licensed under the MIT license.
