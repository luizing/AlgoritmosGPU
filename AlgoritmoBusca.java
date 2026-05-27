import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Objects;

public class AlgoritmoBusca {

    public static String[] carregaTexto(String path) throws IOException {
        String livro = Files.readString(Path.of(path))
                .replaceAll("[^\\p{L}\\s]", " ");
        return livro.split("\\s+");
    }


    public static int contaPalavra(String[] palavras, String palavra){
        int contador = 0;

        for (int i = 0; i <= palavras.length - 1 ; i++){
            if (Objects.equals(palavras[i].toLowerCase(), palavra.toLowerCase())){
                contador++;
            }

        }

        return contador;
    }

    public static int contaPalavraCPU(String[] palavras, String palavra) throws InterruptedException {
        int cores = Runtime.getRuntime().availableProcessors();
        int size = palavras.length / cores;
        int rest = palavras.length % cores;

        Thread[] threads = new Thread[cores];
        int[] resultados = new int[cores];

        int inicio = 0;

        for (int i = 0; i < cores; i++) {

            int fim = inicio + size;

            if (i == cores - 1) {
                fim += rest;
            }

            final int threadIndex = i;
            final int start = inicio;
            final int end = fim;

            threads[i] = new Thread(() -> {
                String[] parte = Arrays.copyOfRange(palavras, start, end);

                resultados[threadIndex] = contaPalavra(parte, palavra);

            });

            threads[i].start();

            inicio = fim;
        }

        for (Thread thread : threads) {
            thread.join();
        }

        int total = 0;

        for (int resultado : resultados) {
            total += resultado;
        }

        return total;
    }

    public static int contaPalavrasGPU(String palavras, String palavra){
        byte [] textoBytes = palavras.getBytes(StandardCharsets.UTF_8);
        byte [] palavraBytes = palavra.getBytes(StandardCharsets.UTF_8);


        return 0;
    }

}
