package dev.breadcrumb.app

import com.google.mlkit.common.MlKitException
import java.io.IOException
import org.json.JSONException

/** Failures surfaced by files, content providers and external services. Cancellation propagates. */
internal inline fun handleExternalFailure(onError: (Exception) -> Unit, action: () -> Unit) {
    try {
        action()
    } catch (error: IOException) {
        onError(error)
    } catch (error: SecurityException) {
        onError(error)
    } catch (error: IllegalArgumentException) {
        onError(error)
    } catch (error: JSONException) {
        onError(error)
    } catch (error: MlKitException) {
        onError(error)
    } catch (cancelled: kotlinx.coroutines.CancellationException) {
        throw cancelled
    } catch (error: IllegalStateException) {
        onError(error)
    }
}
