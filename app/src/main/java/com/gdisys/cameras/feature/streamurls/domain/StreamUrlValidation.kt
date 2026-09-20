package com.gdisys.cameras.feature.streamurls.domain

/**
 * Prefixo fixo de toda URL de stream. O host é imposto pelo `network_security_config`, então o
 * usuário preenche apenas a porta e o nome do stream.
 *
 * Também é o texto exibido antes do campo de porta: manter uma única constante impede que o
 * rótulo da tela divirja da URL efetivamente montada.
 */
const val STREAM_URL_HOST_PREFIX = "http://[fd00:20::cafe]:"

private const val MIN_PORT = 1
private const val MAX_PORT = 65535

private val STREAM_NAME_REGEX = Regex("^[A-Za-z0-9_-]+$")

/** Resultado da validação de uma nova URL de stream. */
sealed interface StreamUrlValidationResult {
  /** URL válida e ainda não cadastrada, já montada a partir da porta e do nome. */
  data class Valid(val url: String) : StreamUrlValidationResult

  /** Porta ausente, não numérica ou fora da faixa [1, 65535]. */
  data object InvalidPort : StreamUrlValidationResult

  /** Nome vazio ou com caracteres fora de `^[A-Za-z0-9_-]+$`. */
  data object InvalidStreamName : StreamUrlValidationResult

  /** URL já presente na lista, comparada sem diferenciar maiúsculas de minúsculas. */
  data object DuplicateUrl : StreamUrlValidationResult
}

/**
 * Valida porta e nome do stream e monta a URL completa.
 *
 * A porta é normalizada para a sua forma numérica (`"0080"` → `"80"`), o que também evita
 * duplicatas que difeririam apenas por zeros à esquerda. O nome é preservado exatamente como
 * digitado; a checagem de duplicidade contra [existingUrls], no entanto, ignora a caixa.
 */
fun validateStreamUrl(
  port: String,
  streamName: String,
  existingUrls: List<String>
): StreamUrlValidationResult {
  val parsedPort = port.toIntOrNull()
  if (parsedPort == null || parsedPort !in MIN_PORT..MAX_PORT) {
    return StreamUrlValidationResult.InvalidPort
  }

  if (!STREAM_NAME_REGEX.matches(streamName)) {
    return StreamUrlValidationResult.InvalidStreamName
  }

  val url = "$STREAM_URL_HOST_PREFIX$parsedPort/$streamName"
  if (existingUrls.any { it.equals(url, ignoreCase = true) }) {
    return StreamUrlValidationResult.DuplicateUrl
  }

  return StreamUrlValidationResult.Valid(url)
}
