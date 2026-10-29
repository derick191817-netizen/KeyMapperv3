import com.example.keymapper.display.ScreenGeometryPolicy
fun main() {
    val base = ScreenGeometryPolicy.Snapshot(0, 1600, 900, 240)
    check(ScreenGeometryPolicy.classify(base, base.copy()) == ScreenGeometryPolicy.Change.NONE)
    check(ScreenGeometryPolicy.classify(base, base.copy(width = 900, height = 1600)) == ScreenGeometryPolicy.Change.RESIZE)
    check(ScreenGeometryPolicy.classify(base, base.copy(displayId = 2)) == ScreenGeometryPolicy.Change.REINITIALIZE)
    check(ScreenGeometryPolicy.classify(base, base.copy(displayId = 1, width = 900, height = 1600)) == ScreenGeometryPolicy.Change.REINITIALIZE)
    check(ScreenGeometryPolicy.classify(base, base.copy(densityDpi = 280, width = 900, height = 1600)) == ScreenGeometryPolicy.Change.REINITIALIZE)
    println("5/5 screen geometry smoke assertions passed")
}
