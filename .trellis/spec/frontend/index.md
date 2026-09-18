# Frontend Development Guidelines

> Best practices for frontend development in this project.

---

## Overview

This directory contains guidelines for frontend development. Fill in each file with your project's specific conventions.

---

## Guidelines Index

| Guide | Description | Status |
|-------|-------------|--------|
| [Directory Structure](./directory-structure.md) | Module organization and file layout | To fill |
| [Component Guidelines](./component-guidelines.md) | Component patterns, props, composition | **Filled**（Compose 组件约定 + feed 交互契约） |
| [Hook Guidelines](./hook-guidelines.md) | Custom hooks, data fetching patterns | N/A（本项目是 Jetpack Compose，无 React hooks） |
| [State Management](./state-management.md) | Local state, global state, server state | **Filled**（状态分层 / 派生状态 / effect 闭包陷阱） |
| [Quality Guidelines](./quality-guidelines.md) | Code standards, forbidden patterns | To fill |
| [Type Safety](./type-safety.md) | Type patterns, validation | **Filled**（显式类型参数 = 元素类型；无 JDK 本地不能编译的自查清单） |

---

## How to Fill These Guidelines

For each guideline file:

1. Document your project's **actual conventions** (not ideals)
2. Include **code examples** from your codebase
3. List **forbidden patterns** and why
4. Add **common mistakes** your team has made

The goal is to help AI assistants and new team members understand how YOUR project works.

---

**Language**: 本项目 spec 使用**中文**，与 `prd.md` / `design.md` / 代码注释及 `android-app/README.md` 保持一致。
（模板原文要求英文；本仓库全部既有文档均为中文，故按项目实际语言填写。如需切换请一次性改全部 spec。）
