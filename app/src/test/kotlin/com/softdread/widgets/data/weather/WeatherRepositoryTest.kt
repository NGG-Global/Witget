package com.softdread.widgets.data.weather

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.softdread.widgets.data.prefs.SavedLocation
import com.softdread.widgets.domain.logic.WeatherReading
import java.io.File
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The two rules the widget path depends on.
 *
 * **One cache entry per place.** The pack lets a widget be placed more than
 * once with its own configuration, so two Weather tiles set to different cities
 * are a supported arrangement. A single cache slot made them evict each other
 * on every refresh: neither ever had a usable cache, so both fetched on every
 * update and neither could fall back when the network was gone.
 *
 * **A widget build does not wait on the network.** A build can be running
 * inside a broadcast — the platform's periodic update, or a tap — so with
 * anything cached for that place it renders the cache and lets the background
 * worker do the fetching.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class WeatherRepositoryTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    private class FakeProvider(
        var reading: WeatherReading,
        var failing: Boolean = false,
    ) : WeatherProvider {
        var calls = 0
        override val attribution = "fake"
        override val requiresApiKey = false
        override suspend fun fetch(query: WeatherQuery): WeatherFetch {
            calls++
            return if (failing) {
                WeatherFetch.Failure(FailureReason.NETWORK)
            } else {
                WeatherFetch.Success(reading.copy(locationName = query.locationName))
            }
        }
    }

    private fun reading(temperature: Double) = WeatherReading(
        temperatureCelsius = temperature,
        feelsLikeCelsius = temperature,
        weatherCode = 0,
        windSpeedKmh = 0.0,
        windGustKmh = 0.0,
        uvIndex = null,
        rainChancePercent = null,
        highCelsius = null,
        lowCelsius = null,
    )

    private val telAviv = SavedLocation("tel aviv", 32.08, 34.78)
    private val london = SavedLocation("london", 51.51, -0.13)

    private companion object {
        val counter = AtomicInteger()
    }

    /**
     * A store per test. `preferencesDataStore` hands out one process-wide
     * instance, so tests sharing it would inherit each other's cache.
     */
    private fun store(): DataStore<Preferences> = PreferenceDataStoreFactory.create {
        File(context.cacheDir, "weather-test-${'$'}{counter.incrementAndGet()}.preferences_pb")
            .also { it.delete() }
    }

    @Test
    fun `two locations keep independent cache entries`() = runTest {
        val provider = FakeProvider(reading(30.0))
        val repository = WeatherRepository(context, provider, store())

        provider.reading = reading(30.0)
        repository.state(savedLocation = telAviv, useDeviceLocation = false)
        provider.reading = reading(11.0)
        repository.state(savedLocation = london, useDeviceLocation = false)
        assertThat(provider.calls).isEqualTo(2)

        // Both are now cached and fresh, so neither reaches the provider again.
        val warmTelAviv = repository.state(savedLocation = telAviv, useDeviceLocation = false)
        val warmLondon = repository.state(savedLocation = london, useDeviceLocation = false)
        assertThat(provider.calls).isEqualTo(2)
        assertThat((warmTelAviv as WeatherState.Ready).reading.temperatureCelsius).isEqualTo(30.0)
        assertThat((warmLondon as WeatherState.Ready).reading.temperatureCelsius).isEqualTo(11.0)
    }

    @Test
    fun `a widget build serves a stale cache rather than waiting on the network`() = runTest {
        val provider = FakeProvider(reading(30.0))
        val repository = WeatherRepository(context, provider, store())
        repository.state(savedLocation = telAviv, useDeviceLocation = false)
        assertThat(provider.calls).isEqualTo(1)

        // forceRefresh mimics a cache that has aged past its freshness window.
        val state = repository.state(
            savedLocation = telAviv,
            useDeviceLocation = false,
            forceRefresh = true,
            allowNetworkWhenCached = false,
        )
        assertThat(provider.calls).isEqualTo(1)
        assertThat(state).isInstanceOf(WeatherState.Ready::class.java)
        assertThat((state as WeatherState.Ready).isStale).isTrue()
    }

    @Test
    fun `a widget with nothing cached for its place still fetches`() = runTest {
        val provider = FakeProvider(reading(30.0))
        val repository = WeatherRepository(context, provider, store())
        repository.state(savedLocation = telAviv, useDeviceLocation = false)

        val state = repository.state(
            savedLocation = london,
            useDeviceLocation = false,
            allowNetworkWhenCached = false,
        )
        assertThat(provider.calls).isEqualTo(2)
        assertThat((state as WeatherState.Ready).isStale).isFalse()
    }

    @Test
    fun `a failed fetch falls back to that location's own cache`() = runTest {
        val provider = FakeProvider(reading(30.0))
        val repository = WeatherRepository(context, provider, store())
        repository.state(savedLocation = telAviv, useDeviceLocation = false)

        provider.failing = true
        val state = repository.state(
            savedLocation = telAviv,
            useDeviceLocation = false,
            forceRefresh = true,
        )
        assertThat((state as WeatherState.Ready).isStale).isTrue()
        assertThat(state.reading.temperatureCelsius).isEqualTo(30.0)
    }

    @Test
    fun `an unknown place with no cache and no network reports unavailable`() = runTest {
        val provider = FakeProvider(reading(30.0), failing = true)
        val repository = WeatherRepository(context, provider, store())
        val state = repository.state(savedLocation = london, useDeviceLocation = false)
        assertThat(state).isEqualTo(WeatherState.Unavailable)
    }
}
