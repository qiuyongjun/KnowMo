# Type Safety

> 认识么（KnowMo）的类型安全约定。语言：中文，与其它 spec 一致。

---

## Overview

- **安卓**：Kotlin + Jetpack Compose。**没有运行时校验库**（无 Zod / io-ts 等价物）。
- **原型**：`prototype/index.html` 里的原生 JS —— **没有类型系统**，一致性靠「与安卓同契约 + 人工对照」（见 `component-guidelines.md`）。
- **持久化不做强类型反序列化**：`StudyRepository` 用 `org.json` + `optXxx(key, 默认值)` 手工读写（读写对称要求见 `design.md` §4）。

结论：**类型安全基本只能靠编译器**——而这恰是本项目的软肋，见文末 Gotcha。

---

## Convention: 显式类型参数写的是**元素类型**

**What**：`Iterable<T>.sortedWith(comparator: Comparator<in T>)`、`compareBy<T>` / `compareByDescending<T>` 上的显式类型参数，是**集合元素类型**，不是选择器的返回类型。

**Why**：写反了不是「悄悄跑错」，而是直接编译不过——lambda 形参拿到错误类型，取 map 就取不到值。但**这个错误已经在本仓库存活了三轮**（`buildQueue` 里的 `compareByDescending<Int>`，R1 引入、R1–R8 无人发现），因为本机无法编译、CI 从未触发。见 Gotcha。

### Wrong

```kotlin
// 词 id 是 String，类型参数却写成了选择器的返回类型
val learned = scope.sortedWith(
    compareByDescending<Int> { id -> termStates[id]?.lapses ?: 0 }   // id: Int → termStates[Int] 编译不过
        .thenBy { id -> termStates[id]?.let { dueTime(it) } ?: Long.MAX_VALUE }
)
```

### Correct

```kotlin
val learned = scope.sortedWith(
    compareByDescending<String> { id -> termStates[id]?.lapses ?: 0 }  // T = 元素类型 = 词 id
        .thenBy { id -> termStates[id]?.let { dueTime(it) } ?: Long.MAX_VALUE }
)
```

### 症状与定位技巧

| 症状 | 说明 |
|---|---|
| `Type mismatch: inferred type is Int but String was expected` | 报在 map 取值那一行，容易误以为自己键类型写错了——其实错在类型参数 |
| `sortedWith` 参数不匹配（`Comparator<Int>` vs `Comparator<in String>`） | 同一条错误的下游表现 |

**定位技巧**：**先删掉显式类型参数让它自己推断**。推断失败说明 lambda 里真有问题；推断成功，再决定要不要补（以及补什么）。

---

## Forbidden Patterns

- 不用 `as` 掩盖类型问题。
- `!!` **只在刚刚过滤过非空**的地方允许，且必须紧邻：

  ```kotlin
  val learned = scope.filter { termStates[it] != null }   // ← 前置非空保证
  val due = learned.filter { isDue(termStates[it]!!) }    // ← 允许
  ```

- 不为了「提示编译器」而补显式类型参数（看上一节，写错方向就是编译错误）。

---

## Gotcha: 本机无法编译 —— 类型错误只能靠 CI 与逐行通读

> **Warning**：开发机**没有 JDK / Android SDK**（仓库里只有 gradle wrapper properties），`cmd.exe` 被沙箱拒绝，`gradlew` 跑不起来。**任何安卓改动都无法在本地验证编译**；唯一自动防线是 push 后由 `.github/workflows/android-build.yml`（ubuntu + JDK 17 + Gradle 8.7）构建。

**因此：安卓侧改动「编译通过」这个验收项，只有在 push 触发 CI 并变绿之后才算成立。** 本地只能做逐行通读自查，清单：

1. 泛型函数上的**显式类型参数**是否与元素类型一致（上一节）。
2. lambda 形参的**类型推断 / 变量遮蔽**——同一个名字（如 `id`）在「词 id 集合」与「下标」两种上下文里含义不同。
3. 可空性：`!!` 是否都有紧邻的前置非空保证；`?.` / `?:` 是否覆盖空分支。
4. `coerceIn(min, max)` 是否可能构成**空区间**（`min > max` 抛异常）——上界用 `maxOf(0, x)` 兜底。
5. 新增符号是否真实存在、签名是否一致（跨文件引用，尤其 `TTSSpeaker` 这类手工维护的类）。
6. `import` 是否齐全；顶层 `private const val` 与局部 `fun` 的前向引用。
7. 括号 / `when` 分支配平。

**配套做法：每完成一个改动批次就 push 一次（哪怕是 WIP），让 CI 尽早替本地编译。** 本仓库已经吃过「三轮未编译的代码里躺着一个编译错误」的教训。

---

## 相关

- 交互契约与状态机约定：`component-guidelines.md`、`state-management.md`
- 持久化读写对称（`optInt(key, 0)` 缺省读）：`design.md` §4 与 `StudyRepository` 的 KDoc
