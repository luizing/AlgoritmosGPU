import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

public class Main {
    private static final Path DIRETORIO_RESULTADOS = Path.of("resultados-benchmark");

    public static void main(String[] args) throws Exception {
        Locale.setDefault(Locale.US);

        String palavra = "Dracula";
        List<ResultadoBenchmark> resultados = new ArrayList<>();

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
        for (int i = 0; i < titulos.length; i++) {
            String titulo = titulos[i];
            String[] texto = biblioteca[i];
            resultados.add(executarBenchmark(
                    "Serial",
                    titulo,
                    palavra,
                    texto.length,
                    () -> AlgoritmoBusca.contaPalavra(texto, palavra)));
        }
        imprimirTempoTotal("Serial", resultados);

        System.out.println("+=");

        System.out.println("Busca Paralela na CPU: ");
        for (int i = 0; i < titulos.length; i++) {
            String titulo = titulos[i];
            String[] texto = biblioteca[i];
            resultados.add(executarBenchmark(
                    "CPU Paralela",
                    titulo,
                    palavra,
                    texto.length,
                    () -> AlgoritmoBusca.contaPalavraCPU(texto, palavra)));
        }
        imprimirTempoTotal("CPU Paralela", resultados);

        System.out.println("+=");

        System.out.println("Busca Paralela na GPU: ");
        for (int i = 0; i < titulos.length; i++) {
            String titulo = titulos[i];
            String[] texto = biblioteca[i];
            resultados.add(executarBenchmark(
                    "GPU",
                    titulo,
                    palavra,
                    texto.length,
                    () -> BuscaGPU.buscaGPU(new String[][]{texto}, palavra)[0]));
        }
        imprimirTempoTotal("GPU", resultados);

        salvarResultados(resultados);

    }

    private static ResultadoBenchmark executarBenchmark(
            String modelo,
            String titulo,
            String palavra,
            int quantidadePalavras,
            Busca busca) throws Exception {
        long inicio = System.nanoTime();
        int ocorrencias = busca.executar();
        long fim = System.nanoTime();
        double tempoMs = (fim - inicio) / 1e6;

        System.out.printf(
                "%s: %d resultados em %.2f milissegundos%n",
                titulo,
                ocorrencias,
                tempoMs);

        return new ResultadoBenchmark(
                modelo,
                titulo,
                palavra,
                ocorrencias,
                tempoMs,
                quantidadePalavras);
    }

    private static void imprimirTempoTotal(String modelo, List<ResultadoBenchmark> resultados) {
        double total = resultados.stream()
                .filter(resultado -> resultado.modelo.equals(modelo))
                .mapToDouble(resultado -> resultado.tempoMs)
                .sum();

        System.out.printf("%nTempo de execução total: %.2f milissegundos%n", total);
    }

    private static void salvarResultados(List<ResultadoBenchmark> resultados) throws IOException {
        Files.createDirectories(DIRETORIO_RESULTADOS);

        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
        Path csv = DIRETORIO_RESULTADOS.resolve("benchmark-" + timestamp + ".csv");
        Path markdown = DIRETORIO_RESULTADOS.resolve("benchmark-" + timestamp + ".md");
        Path html = DIRETORIO_RESULTADOS.resolve("benchmark-" + timestamp + ".html");

        salvarCsv(resultados, csv);
        salvarMarkdown(resultados, markdown);
        salvarHtml(resultados, html);

        System.out.println("+=");
        System.out.println("Resultados salvos em:");
        System.out.println(csv);
        System.out.println(markdown);
        System.out.println(html);
    }

    private static void salvarCsv(List<ResultadoBenchmark> resultados, Path arquivo) throws IOException {
        try (BufferedWriter writer = Files.newBufferedWriter(arquivo, StandardCharsets.UTF_8)) {
            writer.write("modelo,texto,palavra,ocorrencias,tempo_ms,quantidade_palavras");
            writer.newLine();

            for (ResultadoBenchmark resultado : resultados) {
                writer.write(String.format(
                        Locale.US,
                        "%s,%s,%s,%d,%.4f,%d",
                        csv(resultado.modelo),
                        csv(resultado.titulo),
                        csv(resultado.palavra),
                        resultado.ocorrencias,
                        resultado.tempoMs,
                        resultado.quantidadePalavras));
                writer.newLine();
            }
        }
    }

    private static void salvarMarkdown(List<ResultadoBenchmark> resultados, Path arquivo) throws IOException {
        try (BufferedWriter writer = Files.newBufferedWriter(arquivo, StandardCharsets.UTF_8)) {
            writer.write("# Resultados do benchmark");
            writer.newLine();
            writer.newLine();
            writer.write("| Modelo | Texto | Palavra | Ocorrencias | Tempo (ms) | Quantidade de palavras |");
            writer.newLine();
            writer.write("|---|---|---:|---:|---:|---:|");
            writer.newLine();

            for (ResultadoBenchmark resultado : resultados) {
                writer.write(String.format(
                        Locale.US,
                        "| %s | %s | %s | %d | %.2f | %d |",
                        resultado.modelo,
                        resultado.titulo,
                        resultado.palavra,
                        resultado.ocorrencias,
                        resultado.tempoMs,
                        resultado.quantidadePalavras));
                writer.newLine();
            }
        }
    }

    private static void salvarHtml(List<ResultadoBenchmark> resultados, Path arquivo) throws IOException {
        try (BufferedWriter writer = Files.newBufferedWriter(arquivo, StandardCharsets.UTF_8)) {
            writer.write("""
                    <!doctype html>
                    <html lang="pt-BR">
                    <head>
                        <meta charset="utf-8">
                        <title>Resultados do benchmark</title>
                        <style>
                            body { font-family: Arial, sans-serif; margin: 32px; color: #202124; }
                            table { border-collapse: collapse; width: 100%; margin-bottom: 32px; }
                            th, td { border: 1px solid #d0d7de; padding: 8px 10px; text-align: left; }
                            th { background: #f6f8fa; }
                            td.numero { text-align: right; font-variant-numeric: tabular-nums; }
                            svg { width: 100%; height: auto; border: 1px solid #d0d7de; margin-bottom: 32px; }
                            .serial { fill: #2f6f9f; }
                            .cpu { fill: #3c8d40; }
                            .gpu { fill: #c77700; }
                            .label { fill: #202124; font-size: 12px; }
                            .eixo { stroke: #8c959f; stroke-width: 1; }
                        </style>
                    </head>
                    <body>
                    <h1>Resultados do benchmark</h1>
                    """);

            writer.write("<table><thead><tr><th>Modelo</th><th>Texto</th><th>Palavra</th><th>Ocorrencias</th><th>Tempo (ms)</th><th>Quantidade de palavras</th></tr></thead><tbody>");
            writer.newLine();

            for (ResultadoBenchmark resultado : resultados) {
                writer.write(String.format(
                        Locale.US,
                        "<tr><td>%s</td><td>%s</td><td>%s</td><td class=\"numero\">%d</td><td class=\"numero\">%.2f</td><td class=\"numero\">%d</td></tr>",
                        html(resultado.modelo),
                        html(resultado.titulo),
                        html(resultado.palavra),
                        resultado.ocorrencias,
                        resultado.tempoMs,
                        resultado.quantidadePalavras));
                writer.newLine();
            }

            writer.write("</tbody></table>");
            writer.newLine();
            writer.write("<h2>Tempo total por modelo</h2>");
            writer.newLine();
            writer.write(graficoTempoTotal(resultados));
            writer.newLine();
            writer.write("<h2>Tempo por texto e modelo</h2>");
            writer.newLine();
            writer.write(graficoPorTexto(resultados));
            writer.newLine();
            writer.write("</body></html>");
        }
    }

    private static String graficoTempoTotal(List<ResultadoBenchmark> resultados) {
        List<String> modelos = resultados.stream()
                .map(resultado -> resultado.modelo)
                .distinct()
                .toList();
        double maiorTempo = modelos.stream()
                .mapToDouble(modelo -> resultados.stream()
                        .filter(resultado -> resultado.modelo.equals(modelo))
                        .mapToDouble(resultado -> resultado.tempoMs)
                        .sum())
                .max()
                .orElse(1.0);

        StringBuilder svg = new StringBuilder();
        svg.append("<svg viewBox=\"0 0 900 260\" role=\"img\">");
        svg.append("<line class=\"eixo\" x1=\"120\" y1=\"210\" x2=\"860\" y2=\"210\" />");

        int x = 150;
        for (String modelo : modelos) {
            double total = resultados.stream()
                    .filter(resultado -> resultado.modelo.equals(modelo))
                    .mapToDouble(resultado -> resultado.tempoMs)
                    .sum();
            int altura = (int) Math.round((total / maiorTempo) * 150);
            svg.append(String.format(
                    Locale.US,
                    "<rect class=\"%s\" x=\"%d\" y=\"%d\" width=\"120\" height=\"%d\" />",
                    classeModelo(modelo),
                    x,
                    210 - altura,
                    altura));
            svg.append(String.format(
                    Locale.US,
                    "<text class=\"label\" x=\"%d\" y=\"230\" text-anchor=\"middle\">%s</text>",
                    x + 60,
                    html(modelo)));
            svg.append(String.format(
                    Locale.US,
                    "<text class=\"label\" x=\"%d\" y=\"%d\" text-anchor=\"middle\">%.2f ms</text>",
                    x + 60,
                    200 - altura,
                    total));
            x += 220;
        }

        svg.append("</svg>");
        return svg.toString();
    }

    private static String graficoPorTexto(List<ResultadoBenchmark> resultados) {
        List<String> titulos = resultados.stream()
                .map(resultado -> resultado.titulo)
                .distinct()
                .toList();
        List<String> modelos = resultados.stream()
                .map(resultado -> resultado.modelo)
                .distinct()
                .toList();
        double maiorTempo = resultados.stream()
                .max(Comparator.comparingDouble(resultado -> resultado.tempoMs))
                .map(resultado -> resultado.tempoMs)
                .orElse(1.0);

        int alturaSvg = 90 + titulos.size() * 110;
        StringBuilder svg = new StringBuilder();
        svg.append(String.format(Locale.US, "<svg viewBox=\"0 0 1000 %d\" role=\"img\">", alturaSvg));

        int y = 55;
        for (String titulo : titulos) {
            svg.append(String.format(
                    Locale.US,
                    "<text class=\"label\" x=\"20\" y=\"%d\">%s</text>",
                    y + 20,
                    html(titulo)));

            int x = 220;
            for (String modelo : modelos) {
                double tempo = resultados.stream()
                        .filter(resultado -> resultado.titulo.equals(titulo) && resultado.modelo.equals(modelo))
                        .findFirst()
                        .map(resultado -> resultado.tempoMs)
                        .orElse(0.0);
                int largura = (int) Math.round((tempo / maiorTempo) * 620);
                svg.append(String.format(
                        Locale.US,
                        "<rect class=\"%s\" x=\"%d\" y=\"%d\" width=\"%d\" height=\"18\" />",
                        classeModelo(modelo),
                        x,
                        y,
                        largura));
                svg.append(String.format(
                        Locale.US,
                        "<text class=\"label\" x=\"%d\" y=\"%d\">%s %.2f ms</text>",
                        x + largura + 8,
                        y + 14,
                        html(modelo),
                        tempo));
                y += 24;
            }
            y += 38;
        }

        svg.append("</svg>");
        return svg.toString();
    }

    private static String classeModelo(String modelo) {
        if (modelo.equals("Serial")) {
            return "serial";
        }

        if (modelo.equals("CPU Paralela")) {
            return "cpu";
        }

        return "gpu";
    }

    private static String csv(String valor) {
        return "\"" + valor.replace("\"", "\"\"") + "\"";
    }

    private static String html(String valor) {
        return valor
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }

    private interface Busca {
        int executar() throws Exception;
    }

    private static class ResultadoBenchmark {
        private final String modelo;
        private final String titulo;
        private final String palavra;
        private final int ocorrencias;
        private final double tempoMs;
        private final int quantidadePalavras;

        private ResultadoBenchmark(
                String modelo,
                String titulo,
                String palavra,
                int ocorrencias,
                double tempoMs,
                int quantidadePalavras) {
            this.modelo = modelo;
            this.titulo = titulo;
            this.palavra = palavra;
            this.ocorrencias = ocorrencias;
            this.tempoMs = tempoMs;
            this.quantidadePalavras = quantidadePalavras;
        }
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
