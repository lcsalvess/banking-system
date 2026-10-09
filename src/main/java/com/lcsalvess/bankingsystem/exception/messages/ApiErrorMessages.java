package com.lcsalvess.bankingsystem.exception.messages;

public final class ApiErrorMessages {

    private ApiErrorMessages() {
    }

    // User
    public static final String USERNAME_ALREADY_EXISTS =
            "Nome de usuário já cadastrado.";

    public static final String USER_EMAIL_ALREADY_EXISTS =
            "E-mail já cadastrado.";

    // Authentication and authorization
    public static final String INVALID_CREDENTIALS =
            "Usuário ou senha inválidos.";

    public static final String AUTHENTICATION_FAILED =
            "Não foi possível autenticar o usuário.";

    public static final String ACCESS_DENIED_RESOURCE =
            "Você não tem permissão para acessar este recurso.";

    public static final String AUTHENTICATED_USER_NOT_FOUND =
            "Nenhum usuário autenticado encontrado.";

    // Address integration
    public static final String POSTAL_CODE_NOT_FOUND =
            "CEP não encontrado em nenhum provedor.";

    public static final String ADDRESS_PROVIDER_EMPTY_RESPONSE =
            "O serviço de consulta de endereços retornou uma resposta vazia.";

    public static final String ADDRESS_PROVIDER_UNAVAILABLE =
            "O serviço de consulta de endereços está temporariamente indisponível.";

    public static final String ADDRESS_PROVIDER_INVALID_RESPONSE =
            "O serviço de consulta de endereços retornou dados inválidos: ";

    //Client
    public static final String CLIENT_NOT_FOUND =
            "Cliente não encontrado.";

    // Account
    public static final String ACCOUNT_NOT_FOUND =
            "Conta não encontrada.";

    public static final String INVALID_ACCOUNT_DIGIT =
            "Dígito da conta inválido.";

    public static final String CHECKING_ACCOUNT_ALREADY_EXISTS =
            "O cliente já possui uma conta corrente.";

    public static final String SAVINGS_ACCOUNT_ALREADY_EXISTS =
            "O cliente já possui uma conta poupança.";

    public static final String ACCOUNT_HAS_BALANCE =
            "Não é possível cancelar uma conta com saldo.";

    public static final String ACCOUNT_IS_NOT_ACTIVE =
            "Não é possível cancelar uma conta que não está ativa.";

    // Transaction
    public static final String TRANSACTION_NOT_FOUND =
            "Transação não encontrada.";

    public static final String ACCOUNT_NOT_ACTIVE =
            "A conta informada não está ativa.";

    public static final String INVALID_AMOUNT =
            "O valor deve ser maior que zero.";

    public static final String INSUFFICIENT_BALANCE =
            "O valor informado é maior do que o saldo.";

    public static final String SAME_SOURCE_AND_DESTINATION_ACCOUNT =
            "A conta de origem não pode ser igual à conta de destino.";

    public static final String ACCOUNT_NOT_SAVINGS =
            "A conta informada não é poupança.";

    public static final String YIELD_NOT_AVAILABLE =
            "A conta ainda não está disponível para receber rendimento.";

    public static final String NO_YIELD_AVAILABLE =
            "Não há rendimento disponível para esta conta.";

    // Data integrity
    public static final String CLIENT_EMAIL_ALREADY_EXISTS =
            "Já existe um cliente cadastrado com este e-mail.";

    public static final String CLIENT_CPF_ALREADY_EXISTS =
            "Já existe um cliente cadastrado com este CPF.";

    public static final String DAILY_YIELD_ALREADY_APPLIED =
            "O rendimento já foi aplicado para esta conta hoje.";

    public static final String DATABASE_INTEGRITY_ERROR =
            "Erro de integridade de dados no banco.";

    // Validation and request errors
    public static final String VALIDATION_ERROR =
            "Erro de validação.";

    public static final String INVALID_REQUEST_DATA =
            "Dados da requisição inválidos.";

    public static final String MISSING_REQUIRED_PARAMETER =
            "Parâmetro de requisição obrigatório ausente.";

    public static final String INVALID_REQUEST_PARAMETER =
            "Parâmetro de requisição inválido.";

    // Infrastructure
    public static final String INTERNAL_SERVER_ERROR =
            "Ocorreu um erro interno no servidor.";
}