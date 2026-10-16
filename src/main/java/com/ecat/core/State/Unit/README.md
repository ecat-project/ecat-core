每增加一类单位，需要在 UnitRegistry.java 的 `UNIT_CLASSES` 数组中添加对应的类。

## ratio 与换算约定

- 同一枚举类内的线性单位共用一个任意但一致的刻度，换算方向固定为
  `targetValue = value * sourceUnit.getRatio() / targetUnit.getRatio()`。
- ratio 不要求以 SI 基本单位归一；例如 `DistanceUnit` 以 mm 为 1，
  `AirVolumeUnit` 以 ppb 为 1000。关键是同类内比例满足物理等式。
- 摄氏度与开尔文这类仿射单位、dB 这类对数单位，不能用本 ratio 机制混用；
  当前只有单刻度时 ratio=1 仅表示“不做数值换算”。
- 有功功率、无功功率、视在功率分别使用独立单位类；三者之间需要功率因数等额外
  电气条件，不能靠同类 ratio 直接互转。
