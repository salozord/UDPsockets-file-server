package tcpserver;
import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class PoolThreads 
{
	private ServerSocket ss;
	private static int nThreads;
	private static Integer nThreadsActivos = 0;
	private static int tiempoMuerte = 0;
	private String archivo;
	private static Integer numeroSesiones=0;
	private static Boolean iniciaConcurrencia;

	public String listarArchivos()
	{
		File directorio = new File("./data");
		String retorno = "";
		String[] nombres = directorio.list();
		for(int i = 0; i < nombres.length; i++)
		{
			if(i == nombres.length-1)
			{
				retorno += (i+1) + ") " + nombres[i];
				break;
			}
			retorno += (i+1) + ") " + nombres[i] + "\n";
		}
		return retorno;
	}

	public void seleccion(BufferedReader br) throws IOException
	{
		while(true){
			System.out.println("Digite el numero de un archivo");
			System.out.println(listarArchivos());
			File directorio = new File("./data");
			// System.out.println(directorio.listFiles().length);
			int arch = Integer.parseInt(br.readLine());
			if(arch < 1 || arch > directorio.listFiles().length)
			{
				System.out.println("Archivo incorrecto");
				continue;
			}
			archivo = directorio.listFiles()[arch-1].getPath();
			break;
		}
		while(true)
		{
			System.out.println("Ingrese el numero de clientes concurrentes a los que se les enviara el archivo");
			int num = Integer.parseInt(br.readLine());
			if(num < 0 || num > 25)
			{
				System.out.println("Solo puede tener entre 1 y 25 clientes concurrentes");
				continue;
			}
			nThreads = num;
			break;
		}
		while(true)
		{
			System.out.println("Ingrese el numero de timeout de las peticiones del cliente en segundos, MAX 10 SEGUNDOS");
			int muerte = Integer.parseInt(br.readLine());
			if(muerte < 0 || muerte > 10)
			{
				System.err.println("Ingrese un tiempo valido");
				continue;
			}
			tiempoMuerte = muerte;
			break;
		}
	}

	public void servidor() throws IOException, InterruptedException
	{
		InputStreamReader isr = new InputStreamReader(System.in);
		BufferedReader br = new BufferedReader(isr);
		int ip = 8080;
		System.out.println("Empezando servidor maestro en puerto " + ip);

		// Crea el socket que escucha en el puerto seleccionado.
		ss = new ServerSocket(ip);
		System.out.println("Socket creado.");

		seleccion(br);

		ExecutorService executor= Executors.newFixedThreadPool(nThreads);
		System.out.println("Pool con "+nThreads+" threads ha sido creado.");
		System.out.println("Esperando solicitudes.");
		iniciaConcurrencia = false;

		while (true) {
			try 
			{ 
				if(numeroSesiones <= 25)
				{
					Socket sc = ss.accept();
					numeroSesiones++;
//					boolean aceptaArchs = false;
//					seAceptan(aceptaArchs);
					boolean aceptaArchs = seAceptan(true);
					executor.execute(new Protocol(sc, aceptaArchs, tiempoMuerte, archivo, this));
				}
				else
				{
					wait();
				}
			} 
			catch (IOException e) {
				System.out.println("Error creando el socket cliente.");
				e.printStackTrace();
			}
		}
	}
	
//	public boolean seAceptan(boolean aceptan)
//	{
//		synchronized (nThreadsActivos) 
//		{
//			synchronized (iniciaConcurrencia) 
//			{
//				if(iniciaConcurrencia == false)
//				{
//					iniciaConcurrencia = true;
//					nThreadsActivos++;
//					aceptan = true;
//				}
//				else
//				{
//					if(nThreadsActivos < nThreads)
//					{
//						aceptan = true;
//						nThreadsActivos++;
//					}
//					else
//					{
//						aceptan = false;
//					}
//				}
//				return aceptan;
//			}
//		}
//	}
	public boolean seAceptan(boolean primeraVez)
	{
		boolean aceptan = false;
		synchronized (nThreadsActivos) 
		{
			synchronized (iniciaConcurrencia) 
			{
				if(iniciaConcurrencia == false)
				{
					iniciaConcurrencia = true;
					//nThreadsActivos++;
					//aceptan = true;
				}
				else
				{
					if(primeraVez) 
					{
						
						if(nThreadsActivos < nThreads)
						{
							nThreadsActivos++;
						}
						
					}
					if(nThreads == nThreadsActivos && nThreads ==  numeroSesiones)
					{
						aceptan = true;
					}
					else if(numeroSesiones > nThreads ) 
					{
						aceptan = false;
					}
//					ESTO SOBRA SI ES ASÍ CON RETURN
//					else
//					{
//						aceptan = false;
//					}
				}
				return aceptan;
			}
		}
	}
	
	public boolean siguenBloqueadosLosArchivos()
	{
		return iniciaConcurrencia;
	}

	public void finSesionArchivos()
	{
		synchronized(nThreadsActivos)
		{
			synchronized (iniciaConcurrencia) 
			{
				nThreadsActivos--;
				if(nThreadsActivos == 0)
				{
					iniciaConcurrencia = false;
				}
			}
		}
	}

	public void finSesion()
	{
		synchronized (numeroSesiones) {
			numeroSesiones--;
		}
	}
	
	public synchronized void notificar() {
		if(numeroSesiones > 25) {
			notify();
		}
	}

	public static void main(String ... args){
		try {
			PoolThreads pool = new PoolThreads();
			pool.servidor();
		}
		catch (Exception e) {
			e.printStackTrace();
		}
	}
}