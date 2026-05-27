import org.jocl.*;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;

public class BuscaGPU {

    static {
        CL.setExceptionsEnabled(true);
    }

    private static final String KERNEL_SOURCE =
            "__kernel void busca(\n" +
                    "    __global const char* texto,\n" +
                    "    __global const char* palavra,\n" +
                    "    __global int* resultado,\n" +
                    "    int palavraLength,\n" +
                    "    int textoLength)\n" +
                    "{\n" +
                    "    int id = get_global_id(0);\n" +
                    "\n" +
                    "    if(id + palavraLength > textoLength) {\n" +
                    "        return;\n" +
                    "    }\n" +
                    "\n" +
                    "    if(id > 0) {\n" +
                    "        char anterior = texto[id - 1];\n" +
                    "\n" +
                    "        if(anterior >= 'a' && anterior <= 'z') {\n" +
                    "            resultado[id] = 0;\n" +
                    "            return;\n" +
                    "        }\n" +
                    "    }\n" +
                    "\n" +
                    "    for(int i = 0; i < palavraLength; i++) {\n" +
                    "        if(texto[id + i] != palavra[i]) {\n" +
                    "            resultado[id] = 0;\n" +
                    "            return;\n" +
                    "        }\n" +
                    "    }\n" +
                    "\n" +
                    "    if(id + palavraLength < textoLength) {\n" +
                    "        char proximo = texto[id + palavraLength];\n" +
                    "\n" +
                    "        if(proximo >= 'a' && proximo <= 'z') {\n" +
                    "            resultado[id] = 0;\n" +
                    "            return;\n" +
                    "        }\n" +
                    "    }\n" +
                    "\n" +
                    "    resultado[id] = 1;\n" +
                    "}";

    public static byte[] carregarTexto(String[] livro) throws IOException {
        String texto = String.join(" ", livro).toLowerCase().replaceAll("[^a-zA-Z\\s]", " ");
        return texto.getBytes(StandardCharsets.UTF_8);
    }

    public static int buscaGPU(byte[] texto, String palavraBusca) {
        return buscaGPU(new byte[][]{texto}, palavraBusca)[0];
    }

    public static int[] buscaGPU(String[][] biblioteca, String palavraBusca) throws IOException {
        byte[][] textos = new byte[biblioteca.length][];

        for (int i = 0; i < biblioteca.length; i++) {
            textos[i] = carregarTexto(biblioteca[i]);
        }

        return buscaGPU(textos, palavraBusca);
    }

    public static int[] buscaGPU(byte[][] textos, String palavraBusca) {
        int[] totais = new int[textos.length];

        String palavraNormalizada = normalizar(palavraBusca).trim();

        if (palavraNormalizada.isEmpty()) {
            return totais;
        }

        byte[] palavra = palavraNormalizada.getBytes(StandardCharsets.US_ASCII);
        OpenCLBusca openCL = null;

        try {
            openCL = inicializarOpenCL();

            for (int i = 0; i < textos.length; i++) {
                byte[] texto = normalizar(new String(textos[i], StandardCharsets.UTF_8))
                        .getBytes(StandardCharsets.US_ASCII);

                totais[i] = executarBusca(openCL, texto, palavra);
            }
        } catch (CLException e) {
            for (int i = 0; i < textos.length; i++) {
                totais[i] = buscaCPU(textos[i], palavraNormalizada);
            }
        } finally {
            liberar(openCL);
        }

        return totais;
    }

    private static OpenCLBusca inicializarOpenCL() {
        int[] numeroPlataformas = new int[1];
        CL.clGetPlatformIDs(0, null, numeroPlataformas);

        cl_platform_id[] plataformas = new cl_platform_id[numeroPlataformas[0]];
        CL.clGetPlatformIDs(plataformas.length, plataformas, null);

        cl_device_id device = null;

        for (cl_platform_id plataforma : plataformas) {
            int[] numeroDispositivos = new int[1];

            try {
                CL.clGetDeviceIDs(plataforma, CL.CL_DEVICE_TYPE_GPU, 0, null, numeroDispositivos);
            } catch (CLException e) {
                continue;
            }

            if (numeroDispositivos[0] == 0) {
                continue;
            }

            cl_device_id[] devices = new cl_device_id[numeroDispositivos[0]];
            CL.clGetDeviceIDs(plataforma, CL.CL_DEVICE_TYPE_GPU, devices.length, devices, null);
            device = devices[0];
            break;
        }

        if (device == null) {
            throw new CLException("Nenhum dispositivo GPU OpenCL encontrado");
        }

        cl_context context = null;
        cl_command_queue queue = null;
        cl_program program = null;
        cl_kernel kernel = null;

        try {
            context = CL.clCreateContext(null, 1, new cl_device_id[]{device}, null, null, null);
            queue = CL.clCreateCommandQueue(context, device, 0, null);
            program = CL.clCreateProgramWithSource(context, 1, new String[]{KERNEL_SOURCE}, null, null);
            CL.clBuildProgram(program, 0, null, null, null, null);
            kernel = CL.clCreateKernel(program, "busca", null);

            return new OpenCLBusca(context, queue, program, kernel);
        } catch (CLException e) {
            liberar(new OpenCLBusca(context, queue, program, kernel));
            throw e;
        }
    }

    private static int executarBusca(OpenCLBusca openCL, byte[] texto, byte[] palavra) {
        int textoLength = texto.length;
        int palavraLength = palavra.length;

        if (textoLength < palavraLength) {
            return 0;
        }

        int resultadoLength = textoLength - palavraLength + 1;
        int[] resultado = new int[resultadoLength];

        cl_mem textoBuffer = null;
        cl_mem palavraBuffer = null;
        cl_mem resultadoBuffer = null;

        try {
            textoBuffer = CL.clCreateBuffer(
                    openCL.context,
                    CL.CL_MEM_READ_ONLY | CL.CL_MEM_COPY_HOST_PTR,
                    Sizeof.cl_char * textoLength,
                    Pointer.to(texto),
                    null
            );

            palavraBuffer = CL.clCreateBuffer(
                    openCL.context,
                    CL.CL_MEM_READ_ONLY | CL.CL_MEM_COPY_HOST_PTR,
                    Sizeof.cl_char * palavraLength,
                    Pointer.to(palavra),
                    null
            );

            resultadoBuffer = CL.clCreateBuffer(
                    openCL.context,
                    CL.CL_MEM_READ_WRITE,
                    Sizeof.cl_int * resultadoLength,
                    null,
                    null
            );

            CL.clSetKernelArg(openCL.kernel, 0, Sizeof.cl_mem, Pointer.to(textoBuffer));
            CL.clSetKernelArg(openCL.kernel, 1, Sizeof.cl_mem, Pointer.to(palavraBuffer));
            CL.clSetKernelArg(openCL.kernel, 2, Sizeof.cl_mem, Pointer.to(resultadoBuffer));
            CL.clSetKernelArg(openCL.kernel, 3, Sizeof.cl_int, Pointer.to(new int[]{palavraLength}));
            CL.clSetKernelArg(openCL.kernel, 4, Sizeof.cl_int, Pointer.to(new int[]{textoLength}));

            CL.clEnqueueNDRangeKernel(
                    openCL.queue,
                    openCL.kernel,
                    1,
                    null,
                    new long[]{resultadoLength},
                    null,
                    0,
                    null,
                    null
            );

            CL.clEnqueueReadBuffer(
                    openCL.queue,
                    resultadoBuffer,
                    CL.CL_TRUE,
                    0,
                    Sizeof.cl_int * resultadoLength,
                    Pointer.to(resultado),
                    0,
                    null,
                    null
            );

            int total = 0;

            for (int v : resultado) {
                total += v;
            }

            return total;
        } finally {
            if (resultadoBuffer != null) {
                CL.clReleaseMemObject(resultadoBuffer);
            }

            if (palavraBuffer != null) {
                CL.clReleaseMemObject(palavraBuffer);
            }

            if (textoBuffer != null) {
                CL.clReleaseMemObject(textoBuffer);
            }
        }
    }

    private static int buscaCPU(byte[] texto, String palavra) {
        String textoNormalizado = normalizar(new String(texto, StandardCharsets.UTF_8));
        int contador = 0;
        int posicao = 0;

        while (posicao < textoNormalizado.length()) {
            int proximaPosicao = textoNormalizado.indexOf(palavra, posicao);

            if (proximaPosicao == -1) {
                break;
            }

            boolean inicioPalavra = proximaPosicao == 0 || textoNormalizado.charAt(proximaPosicao - 1) == ' ';
            int fim = proximaPosicao + palavra.length();
            boolean fimPalavra = fim == textoNormalizado.length() || textoNormalizado.charAt(fim) == ' ';

            if (inicioPalavra && fimPalavra) {
                contador++;
            }

            posicao = proximaPosicao + 1;
        }

        return contador;
    }

    private static void liberar(OpenCLBusca openCL) {
        if (openCL == null) {
            return;
        }

        if (openCL.kernel != null) {
            CL.clReleaseKernel(openCL.kernel);
        }

        if (openCL.program != null) {
            CL.clReleaseProgram(openCL.program);
        }

        if (openCL.queue != null) {
            CL.clReleaseCommandQueue(openCL.queue);
        }

        if (openCL.context != null) {
            CL.clReleaseContext(openCL.context);
        }
    }

    private static class OpenCLBusca {
        private final cl_context context;
        private final cl_command_queue queue;
        private final cl_program program;
        private final cl_kernel kernel;

        private OpenCLBusca(
                cl_context context,
                cl_command_queue queue,
                cl_program program,
                cl_kernel kernel) {
            this.context = context;
            this.queue = queue;
            this.program = program;
            this.kernel = kernel;
        }
    }

    public static String normalizar(String texto) {

        texto = texto.toLowerCase();

        texto = Normalizer.normalize(
                texto,
                Normalizer.Form.NFD
        );

        texto = texto.replaceAll(
                "\\p{InCombiningDiacriticalMarks}+",
                ""
        );

        texto = texto.replaceAll(
                "[^a-z\\s]",
                " "
        );

        return texto;
    }
}
