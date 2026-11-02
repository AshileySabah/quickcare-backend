package com.quickcare.backend.common.validation;

public final class DocumentosBrasileiros {

    private DocumentosBrasileiros() {
    }

    public static boolean cpfValido(String cpf) {
        if (cpf == null || !cpf.matches("\\d{11}") || todosDigitosIguais(cpf)) {
            return false;
        }

        int[] digitos = digitos(cpf);
        return digitos[9] == digitoModulo11(digitos, 9, 10) && digitos[10] == digitoModulo11(digitos, 10, 11);
    }

    public static boolean cnpjValido(String cnpj) {
        if (cnpj == null || !cnpj.matches("\\d{14}") || todosDigitosIguais(cnpj)) {
            return false;
        }

        int[] digitos = digitos(cnpj);
        int[] pesos1 = {5, 4, 3, 2, 9, 8, 7, 6, 5, 4, 3, 2};
        int[] pesos2 = {6, 5, 4, 3, 2, 9, 8, 7, 6, 5, 4, 3, 2};
        return digitos[12] == digitoCnpj(digitos, pesos1) && digitos[13] == digitoCnpj(digitos, pesos2);
    }

    public static boolean cnhValida(String cnh) {
        if (cnh == null || !cnh.matches("\\d{11}") || todosDigitosIguais(cnh)) {
            return false;
        }

        int[] digitos = digitos(cnh);

        int soma = 0;
        for (int i = 0, peso = 9; i < 9; i++, peso--) {
            soma += digitos[i] * peso;
        }
        int dv1 = soma % 11;
        int desconto = 0;
        if (dv1 >= 10) {
            dv1 = 0;
            desconto = 2;
        }

        soma = 0;
        for (int i = 0, peso = 1; i < 9; i++, peso++) {
            soma += digitos[i] * peso;
        }
        int resto = soma % 11;
        int dv2 = resto >= 10 ? 0 : resto - desconto;

        return digitos[9] == dv1 && digitos[10] == dv2;
    }

    public static boolean rgValido(String rg, String uf) {
        if (rg == null) {
            return false;
        }

        String normalizado = rg.toUpperCase();

        if ("SP".equalsIgnoreCase(uf)) {
            return normalizado.matches("\\d{8}[\\dX]") && rgSpDigitoConfere(normalizado);
        }

        return normalizado.matches("\\d{4,13}[\\dX]");
    }

    private static boolean rgSpDigitoConfere(String rg) {
        int soma = 0;
        for (int i = 0; i < 8; i++) {
            soma += Character.getNumericValue(rg.charAt(i)) * (i + 2);
        }
        int dv = (11 - soma % 11) % 11;
        char esperado = dv == 10 ? 'X' : Character.forDigit(dv, 10);
        return rg.charAt(8) == esperado;
    }

    private static int digitoModulo11(int[] digitos, int quantidade, int pesoInicial) {
        int soma = 0;
        for (int i = 0; i < quantidade; i++) {
            soma += digitos[i] * (pesoInicial - i);
        }
        int resto = soma % 11;
        return resto < 2 ? 0 : 11 - resto;
    }

    private static int digitoCnpj(int[] digitos, int[] pesos) {
        int soma = 0;
        for (int i = 0; i < pesos.length; i++) {
            soma += digitos[i] * pesos[i];
        }
        int resto = soma % 11;
        return resto < 2 ? 0 : 11 - resto;
    }

    private static int[] digitos(String valor) {
        return valor.chars().map(Character::getNumericValue).toArray();
    }

    private static boolean todosDigitosIguais(String valor) {
        return valor.chars().distinct().count() == 1;
    }

}
