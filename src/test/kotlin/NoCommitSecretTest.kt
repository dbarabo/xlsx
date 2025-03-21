
internal val secret = mapOf(
    Tag.AFINA_USER to "FNSUSER",
    Tag.AFINA_PSWD to "NhjKjKj08",
    Tag.AFINA_URL to "jdbc:oracle:thin:@192.168.0.42:1521:AFINA"
)

enum class Tag {
    AFINA_USER,
    AFINA_PSWD,
    AFINA_URL
}