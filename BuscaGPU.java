import org.jocl.*;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.Normalizer;

public class BuscaGPU {

    static {
        CL.setExceptionsEnabled(true);
    }

    public static byte[] carregarTexto(String[] livro) throws IOException {
        String texto = String.join(" ", livro).toLowerCase().replaceAll("[^a-zA-Z\\s]", " ");
        return texto.getBytes(StandardCharsets.UTF_8);
    }

    public static int buscaGPU(byte[] texto, String palavraBusca) {

        String textoNormalizado =
                normalizar(new String(texto, StandardCharsets.UTF_8));

        String palavraNormalizada =
                normalizar(palavraBusca);

        texto = textoNormalizado.getBytes(StandardCharsets.US_ASCII);

        byte[] palavra =
                palavraNormalizada.getBytes(StandardCharsets.US_ASCII);

        int textoLength = texto.length;
        int palavraLength = palavra.length;

        int[] resultado = new int[textoLength];

        // =========================
        // KERNEL OPENCL
        // =========================

        String source =
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
                        "        resultado[id] = 0;\n" +
                        "        return;\n" +
                        "    }\n" +
                        "\n" +
                        "    // =====================\n" +
                        "    // verifica limite anterior\n" +
                        "    // =====================\n" +
                        "\n" +
                        "    if(id > 0) {\n" +
                        "\n" +
                        "        char anterior = texto[id - 1];\n" +
                        "\n" +
                        "        if(\n" +
                        "            (anterior >= 'a' && anterior <= 'z') ||\n" +
                        "            (anterior >= 'A' && anterior <= 'Z')\n" +
                        "        ) {\n" +
                        "            resultado[id] = 0;\n" +
                        "            return;\n" +
                        "        }\n" +
                        "    }\n" +
                        "\n" +
                        "    // =====================\n" +
                        "    // compara palavra\n" +
                        "    // =====================\n" +
                        "\n" +
                        "    for(int i = 0; i < palavraLength; i++) {\n" +
                        "\n" +
                        "        char c1 = texto[id + i];\n" +
                        "        char c2 = palavra[i];\n" +
                        "\n" +
                        "        // lowercase manual\n" +
                        "        if(c1 >= 'A' && c1 <= 'Z')\n" +
                        "            c1 += 32;\n" +
                        "\n" +
                        "        if(c2 >= 'A' && c2 <= 'Z')\n" +
                        "            c2 += 32;\n" +
                        "\n" +
                        "        if(c1 != c2) {\n" +
                        "            resultado[id] = 0;\n" +
                        "            return;\n" +
                        "        }\n" +
                        "    }\n" +
                        "\n" +
                        "    // =====================\n" +
                        "    // verifica limite posterior\n" +
                        "    // =====================\n" +
                        "\n" +
                        "    if(id + palavraLength < textoLength) {\n" +
                        "\n" +
                        "        char proximo = texto[id + palavraLength];\n" +
                        "\n" +
                        "        if(\n" +
                        "            (proximo >= 'a' && proximo <= 'z') ||\n" +
                        "            (proximo >= 'A' && proximo <= 'Z')\n" +
                        "        ) {\n" +
                        "            resultado[id] = 0;\n" +
                        "            return;\n" +
                        "        }\n" +
                        "    }\n" +
                        "\n" +
                        "    resultado[id] = 1;\n" +
                        "}";

        // =========================
        // PLATAFORMA
        // =========================

        cl_platform_id[] platforms = new cl_platform_id[1];
        CL.clGetPlatformIDs(1, platforms, null);

        cl_platform_id platform = platforms[0];

        // =========================
        // GPU
        // =========================

        cl_device_id[] devices = new cl_device_id[1];

        CL.clGetDeviceIDs(
                platform,
                CL.CL_DEVICE_TYPE_GPU,
                1,
                devices,
                null
        );

        cl_device_id device = devices[0];

        // =========================
        // CONTEXTO
        // =========================

        cl_context context = CL.clCreateContext(
                null,
                1,
                new cl_device_id[]{device},
                null,
                null,
                null
        );

        // =========================
        // COMMAND QUEUE
        // =========================

        cl_command_queue queue =
                CL.clCreateCommandQueue(
                        context,
                        device,
                        0,
                        null
                );

        // =========================
        // BUFFERS
        // =========================

        cl_mem textoBuffer = CL.clCreateBuffer(
                context,
                CL.CL_MEM_READ_ONLY |
                        CL.CL_MEM_COPY_HOST_PTR,
                Sizeof.cl_char * textoLength,
                Pointer.to(texto),
                null
        );

        cl_mem palavraBuffer = CL.clCreateBuffer(
                context,
                CL.CL_MEM_READ_ONLY |
                        CL.CL_MEM_COPY_HOST_PTR,
                Sizeof.cl_char * palavraLength,
                Pointer.to(palavra),
                null
        );

        cl_mem resultadoBuffer = CL.clCreateBuffer(
                context,
                CL.CL_MEM_READ_WRITE,
                Sizeof.cl_int * textoLength,
                null,
                null
        );

        // =========================
        // PROGRAMA
        // =========================

        cl_program program =
                CL.clCreateProgramWithSource(
                        context,
                        1,
                        new String[]{source},
                        null,
                        null
                );

        CL.clBuildProgram(program, 0, null, null, null, null);

        // =========================
        // KERNEL
        // =========================

        cl_kernel kernel =
                CL.clCreateKernel(program, "busca", null);

        // argumentos

        CL.clSetKernelArg(
                kernel,
                0,
                Sizeof.cl_mem,
                Pointer.to(textoBuffer)
        );

        CL.clSetKernelArg(
                kernel,
                1,
                Sizeof.cl_mem,
                Pointer.to(palavraBuffer)
        );

        CL.clSetKernelArg(
                kernel,
                2,
                Sizeof.cl_mem,
                Pointer.to(resultadoBuffer)
        );

        CL.clSetKernelArg(
                kernel,
                3,
                Sizeof.cl_int,
                Pointer.to(new int[]{palavraLength})
        );

        CL.clSetKernelArg(
                kernel,
                4,
                Sizeof.cl_int,
                Pointer.to(new int[]{textoLength})
        );

        // =========================
        // EXECUTA
        // =========================

        long[] globalWorkSize =
                new long[]{
                        textoLength - palavraLength + 1
                };

        CL.clEnqueueNDRangeKernel(
                queue,
                kernel,
                1,
                null,
                globalWorkSize,
                null,
                0,
                null,
                null
        );

        // =========================
        // LÊ RESULTADO
        // =========================

        CL.clEnqueueReadBuffer(
                queue,
                resultadoBuffer,
                CL.CL_TRUE,
                0,
                Sizeof.cl_int * textoLength,
                Pointer.to(resultado),
                0,
                null,
                null
        );

        // =========================
        // SOMA
        // =========================

        int total = 0;

        for (int v : resultado) {
            total += v;
        }

        // =========================
        // CLEANUP
        // =========================

        CL.clReleaseMemObject(textoBuffer);
        CL.clReleaseMemObject(palavraBuffer);
        CL.clReleaseMemObject(resultadoBuffer);

        CL.clReleaseKernel(kernel);
        CL.clReleaseProgram(program);

        CL.clReleaseCommandQueue(queue);
        CL.clReleaseContext(context);

        return total;
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