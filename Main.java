import java.io.IOException;

public class Main {
    public static void main(String[] args) throws IOException, InterruptedException {
        long start = System.nanoTime();

        String palavra = "Dracula";

        String[] DicionarioBR = AlgoritmoBusca.carregaTexto("br-sem-acentos.txt");
        String[] Dracula = AlgoritmoBusca.carregaTexto("Dracula-165307.txt");
        String[] DonQuixote = AlgoritmoBusca.carregaTexto("DonQuixote-388208.txt");
        String[] WarAndPeace = AlgoritmoBusca.carregaTexto("WarAndPeace.txt");


        String[][] b1 = {DicionarioBR,Dracula,DonQuixote,WarAndPeace};

        String[] Todos3x = repetirBiblioteca(b1, 3);

        String[][] biblioteca = {DicionarioBR,Dracula,DonQuixote,WarAndPeace,Todos3x};

        String[] titulos = {"DicionarioBR","Dracula","Don Quixote", "War and Peace", "Todos os livros, 3x"};

        System.out.println("+=");

        System.out.println("Busca serial: ");
        long startSerial = System.nanoTime();
        for (int i =0; i < titulos.length ; i++){
            long inicio = System.nanoTime();
            int valor = AlgoritmoBusca.contaPalavra(biblioteca[i], palavra);
            long fim = System.nanoTime();

            System.out.printf(
                    "%s: %d resultados em %.2f milissegundos%n",
                    titulos[i],
                    valor,
                    ((fim - inicio) / 1e6));
        }
        System.out.printf("\nTempo de execução total: %.2f milissegundos%n",
                (System.nanoTime()-startSerial)/1e6);

        System.out.println("+=");

        System.out.println("Busca Paralela na CPU: ");
        long startCPU = System.nanoTime();
        for (int i =0; i < titulos.length ; i++){
            long inicio = System.nanoTime();
            int valor = AlgoritmoBusca.contaPalavraCPU(biblioteca[i], palavra);
            long fim = System.nanoTime();

            System.out.printf(
                    "%s: %d resultados em %.2f milissegundos%n",
                    titulos[i],
                    valor,
                    ((fim - inicio) / 1e6));
        }
        System.out.printf("\nTempo de execução total: %.2f milissegundos%n",
                (System.nanoTime()-startCPU)/1e6);

        System.out.println("+=");

        System.out.println("Busca Paralela na GPU: ");
        long startGPU = System.nanoTime();
        long inicioGPU = System.nanoTime();
        int[] resultadosGPU = BuscaGPU.buscaGPU(biblioteca, palavra);
        double tempoTotalGPU = (System.nanoTime() - inicioGPU) / 1e6;

        for (int i =0; i < titulos.length ; i++){
            System.out.printf(
                    "%s: %d resultados%n",
                    titulos[i],
                    resultadosGPU[i]);
        }
        System.out.printf("Busca GPU em lote: %.2f milissegundos%n", tempoTotalGPU);
        System.out.printf("\nTempo de execução total: %.2f milissegundos%n",
                (System.nanoTime()-startGPU)/1e6);


//        System.out.printf("\nTempo de execução total: %.2f milissegundos%n",
//                (System.nanoTime()-start)/1e6);

    }

    public static String[] repetirBiblioteca(
            String[][] biblioteca,
            int repeticoes) {

        // calcula tamanho total
        int total = 0;

        for (String[] livro : biblioteca) {
            total += livro.length;
        }

        total *= repeticoes;

        // array final
        String[] resultado = new String[total];

        int indice = 0;

        // repete os livros
        for (int r = 0; r < repeticoes; r++) {

            for (String[] livro : biblioteca) {

                for (String palavra : livro) {

                    resultado[indice++] = palavra;
                }
            }
        }

        return resultado;
    }


}
