package io.throne.bbox.flutter

import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.FlutterEngine
import io.throne.bbox.BoxBridge

/**
 * The entry point the launcher and the TV leanback banner point at.
 *
 * Both channels live in [BoxBridge]; the activity only owns the lifecycle.
 */
class BboxFlutterActivity : FlutterActivity() {

    private var bridge: BoxBridge? = null

    override fun configureFlutterEngine(flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)
        bridge = BoxBridge(flutterEngine.dartExecutor.binaryMessenger, this).also { it.attach() }
    }

    override fun cleanUpFlutterEngine(flutterEngine: FlutterEngine) {
        bridge?.detach()
        bridge = null
        super.cleanUpFlutterEngine(flutterEngine)
    }

    override fun onDestroy() {
        bridge?.detach()
        bridge = null
        super.onDestroy()
    }
}
