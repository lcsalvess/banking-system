package com.lcsalvess.bankingsystem.validation;

public final class ValidationMessages {

    private ValidationMessages() {
    }

    // ==================== SENHAS ====================

    public static final String REQUIRED_PASSWORD =
            "A senha é obrigatória.";

    public static final String PASSWORD_TOO_SHORT =
            "A senha deve ter pelo menos 8 caracteres.";

    public static final String PASSWORD_EXCEEDS_MAX_BYTE_LENGTH =
            "A senha excede o limite de tamanho permitido.";


    // ==================== USUÁRIOS ====================

    public static final String REQUIRED_USERNAME =
            "O nome de usuário é obrigatório.";

    public static final String INVALID_USERNAME_LENGTH =
            "O nome de usuário deve ter no máximo 20 caracteres.";

    public static final String INVALID_USERNAME_FORMAT =
            "O nome de usuário pode conter apenas letras, números, ponto, hífen e underscore.";

    public static final String REQUIRED_USER_ROLE =
            "O perfil do usuário é obrigatório.";


    // ==================== CLIENTES ====================

    public static final String REQUIRED_CLIENT_NAME =
            "O nome é obrigatório.";

    public static final String INVALID_CLIENT_NAME_BLANK =
            "O nome não pode ser vazio.";

    public static final String INVALID_CLIENT_NAME_LENGTH =
            "O nome deve ter no máximo 125 caracteres.";

    public static final String REQUIRED_CPF =
            "O CPF é obrigatório.";

    public static final String INVALID_CPF =
            "O CPF informado é inválido.";

    public static final String INVALID_CPF_LENGTH =
            "O CPF deve conter exatamente 11 números.";

    public static final String REQUIRED_EMAIL =
            "O e-mail é obrigatório.";

    public static final String INVALID_EMAIL =
            "O e-mail deve ser válido.";

    public static final String INVALID_EMAIL_BLANK =
            "O e-mail não pode ser vazio.";

    public static final String INVALID_EMAIL_LENGTH =
            "O e-mail deve ter no máximo 254 caracteres.";

    public static final String REQUIRED_PHONE_NUMBER =
            "O telefone é obrigatório.";

    public static final String INVALID_PHONE_NUMBER_FORMAT =
            "O telefone deve conter de 10 a 11 números, incluindo o DDD.";


    // ==================== ENDEREÇOS ====================

    public static final String REQUIRED_ADDRESS =
            "Os dados de endereço são obrigatórios.";

    public static final String REQUIRED_STREET_NAME =
            "O logradouro é obrigatório.";

    public static final String REQUIRED_STREET_NUMBER =
            "O número é obrigatório.";

    public static final String STREET_NUMBER_EXCEEDS_MAX_LENGTH =
            "O número deve ter no máximo 10 caracteres.";

    public static final String REQUIRED_NEIGHBORHOOD =
            "O bairro é obrigatório.";

    public static final String REQUIRED_CITY =
            "A cidade é obrigatória.";

    public static final String REQUIRED_STATE =
            "O estado é obrigatório.";

    public static final String INVALID_STATE =
            "A sigla do estado informada não corresponde a uma UF válida.";

    public static final String REQUIRED_POSTAL_CODE =
            "O CEP é obrigatório.";

    public static final String INVALID_POSTAL_CODE_FORMAT =
            "O CEP deve conter exatamente 8 números, sem traços ou espaços.";

    public static final String INVALID_COMPLEMENT_LENGTH =
            "O complemento deve ter no máximo 100 caracteres.";


    // ==================== CONTAS ====================

    public static final String REQUIRED_HOLDER_ID =
            "O ID do titular é obrigatório.";

    public static final String POSITIVE_HOLDER_ID =
            "O ID do titular deve ser maior que zero.";

    public static final String REQUIRED_ACCOUNT_TYPE =
            "O tipo de conta é obrigatório.";

    public static final String INVALID_ACCOUNT_NUMBER_LENGTH =
            "O número da conta deve conter entre 5 e 20 dígitos.";

    public static final String INVALID_ACCOUNT_CHECK_DIGIT_LENGTH =
            "O dígito verificador da conta deve conter exatamente 1 dígito.";


    // ==================== TRANSFERÊNCIAS ====================

    public static final String REQUIRED_SOURCE_ACCOUNT_NUMBER =
            "O número da conta de origem é obrigatório.";

    public static final String REQUIRED_SOURCE_ACCOUNT_DIGIT =
            "O dígito da conta de origem é obrigatório.";

    public static final String REQUIRED_DESTINATION_ACCOUNT_NUMBER =
            "O número da conta de destino é obrigatório.";

    public static final String REQUIRED_DESTINATION_ACCOUNT_DIGIT =
            "O dígito da conta de destino é obrigatório.";

    public static final String REQUIRED_TRANSFER_AMOUNT =
            "O valor da transferência é obrigatório.";

    public static final String POSITIVE_TRANSFER_AMOUNT =
            "O valor da transferência deve ser maior que zero.";


    // ==================== OPERAÇÕES DE CONTA ====================

    public static final String REQUIRED_ACCOUNT_NUMBER =
            "O número da conta é obrigatório.";

    public static final String REQUIRED_ACCOUNT_DIGIT =
            "O dígito da conta é obrigatório.";

    public static final String REQUIRED_OPERATION_AMOUNT =
            "O valor da operação é obrigatório.";

    public static final String POSITIVE_OPERATION_AMOUNT =
            "O valor da operação deve ser maior que zero.";


    // ==================== VALORES MONETÁRIOS ====================

    public static final String INVALID_AMOUNT_PRECISION =
            "O valor deve ter no máximo 13 dígitos inteiros e 2 casas decimais.";

}