import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import com.github.recognized.parse
import com.github.recognized.stringify
import kotlinx.serialization.serializer
import com.github.recognized.server
import kotlinx.serialization.list
val HttpClient.Fuzzer : com.github.recognized.service.Fuzzer get() = FuzzerProxy(this)
class FuzzerProxy(val client: HttpClient) : com.github.recognized.service.Fuzzer {
    override suspend fun generation(offset: kotlin.Int, count: kotlin.Int, sortBy: com.github.recognized.service.SortOrder): kotlin.collections.List<com.github.recognized.service.Snippet> {
        val result___ = client.get<String>(server + "/api/Fuzzer/generation") {
            parameter("offset", stringify(kotlin.Int.serializer(), offset))
            parameter("count", stringify(kotlin.Int.serializer(), count))
            parameter("sortBy", stringify(com.github.recognized.service.SortOrder.serializer(), sortBy))
        }
        return parse(com.github.recognized.service.Snippet.serializer().list, result___)
    }
    override suspend fun start(): kotlin.Unit {
        val result___ = client.get<String>(server + "/api/Fuzzer/start") {
        }
    }
    override suspend fun stat(): com.github.recognized.service.Statistics {
        val result___ = client.get<String>(server + "/api/Fuzzer/stat") {
        }
        return parse(com.github.recognized.service.Statistics.serializer(), result___)
    }
    override suspend fun stop(): kotlin.Unit {
        val result___ = client.get<String>(server + "/api/Fuzzer/stop") {
        }
    }
    override suspend fun togglePause(): kotlin.Unit {
        val result___ = client.get<String>(server + "/api/Fuzzer/togglePause") {
        }
    }
}
