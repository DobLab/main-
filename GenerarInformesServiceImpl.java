package es.congreso.accionSocial.service;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

import org.apache.commons.lang.StringUtils;
import org.apache.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.itextpdf.io.font.FontProgram;
import com.itextpdf.io.font.FontProgramFactory;
import com.itextpdf.io.font.PdfEncodings;
import com.itextpdf.io.font.constants.StandardFonts;
import com.itextpdf.io.image.ImageData;
import com.itextpdf.io.image.ImageDataFactory;
import com.itextpdf.kernel.colors.Color;
import com.itextpdf.kernel.colors.DeviceRgb;
import com.itextpdf.kernel.events.Event;
import com.itextpdf.kernel.events.IEventHandler;
import com.itextpdf.kernel.events.PdfDocumentEvent;
import com.itextpdf.kernel.font.PdfFont;
import com.itextpdf.kernel.font.PdfFontFactory;
import com.itextpdf.kernel.geom.PageSize;
import com.itextpdf.kernel.geom.Rectangle;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.layout.Canvas;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.borders.Border;
import com.itextpdf.layout.element.Cell;
import com.itextpdf.layout.element.Image;
import com.itextpdf.layout.element.Paragraph;
import com.itextpdf.layout.element.Table;
import com.itextpdf.layout.element.Text;
import com.itextpdf.layout.property.HorizontalAlignment;
import com.itextpdf.layout.property.TextAlignment;
import com.itextpdf.layout.property.UnitValue;
import com.itextpdf.layout.property.VerticalAlignment;
import com.itextpdf.styledxmlparser.jsoup.Jsoup;

import es.congreso.accionSocial.dao.SolicitudesPrestacionesDao;
import es.congreso.accionSocial.model.BusquedaSolBean;
import es.congreso.accionSocial.model.ConceptoSolBean;
import es.congreso.accionSocial.model.DocumentoSolBean;
import es.congreso.accionSocial.model.FirmaEscritoBean;
import es.congreso.accionSocial.model.SolicitudBean;

@Service("generarInformesService")
public class GenerarInformesServiceImpl implements GenerarInformesService {

	private static final String PRESTACION_NORMAL = "505002";
	private static final String PRESTACION_ESPECIAL = "505005";

	private static final Logger logger = Logger.getLogger(SolicitudesPrestacionesServiceImpl.class);

	@Autowired
	SolicitudesPrestacionesDao solicitudesPrestacionesDao;

	@Override
	public InputStream generarPDFSolicitud(final SolicitudBean solicitud, Boolean Borrador) throws Exception {
		logger.debug("Entrando en generarPDFSolicitud");

		InputStream dataStreamForPdf = null;

		final SolicitudBean solicitudBD = solicitudesPrestacionesDao.getSolicitud(solicitud);
		final List<ConceptoSolBean> listaConceptos = solicitudesPrestacionesDao.getConceptosSolicitud(solicitud);
		final List<DocumentoSolBean> listaDocumentos = solicitudesPrestacionesDao.getListaDocumentosSolicitud(solicitudBD);
		final String textoNota = this.solicitudesPrestacionesDao.getTextoNota(solicitudBD.getColectivo(), solicitudBD.getTipo(),
		        solicitudBD.getSubtipo());
		final String proteccionDatos =
		        this.solicitudesPrestacionesDao.getTextoPrda(this.solicitudesPrestacionesDao.getPrdaIdActual());
		final ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
		final PdfDocument pdfDoc = new PdfDocument(new PdfWriter(outputStream));
		final Document doc = new Document(pdfDoc, PageSize.A4);

		final SolicitudEventHandler solicitudEventHandler = new SolicitudEventHandler(doc);

		pdfDoc.addEventHandler(PdfDocumentEvent.END_PAGE, solicitudEventHandler);

		doc.setMargins(30, 20, 40, 20);

		final Color colorFondo = new DeviceRgb(255, 255, 205);

		final PdfFont font = PdfFontFactory.createFont(StandardFonts.TIMES_ROMAN);
		doc.setFont(font);
		doc.setFontSize(10);
		doc.setTextAlignment(TextAlignment.JUSTIFIED);
		
		// Añadimos el escudo
		try {
			final byte[] imagenBytes = solicitudesPrestacionesDao.getEscudoEscrito(solicitudBD.getColectivo());
			final ImageData imageData = ImageDataFactory.create(imagenBytes);
			final Image imagen = new Image(imageData);
			imagen.scale(0.1f, 0.1f).setPadding(0).setMarginLeft(5);
			doc.add(imagen);
		} catch (final Exception e) {
			logger.error(e.getMessage());
		}

		Text texto = new Text("REGLAMENTO FONDO PRESTACIONES SOCIALES").setTextAlignment(TextAlignment.RIGHT).setFontSize(8);
		Paragraph cabecera = new Paragraph().add(texto).setTextAlignment(TextAlignment.RIGHT).setPadding(0).setMargin(0)
		        .setMultipliedLeading(1).setMarginRight(10);
		doc.add(cabecera);

		String colectivo = "";
		if (solicitudBD.getColectivo().equals("F")) {
			colectivo = "FUNCIONARIOS CORTES GENERALES";
		} else if (solicitudBD.getColectivo().equals("L")) {
			colectivo = "PERSONAL LABORAL CONGRESO DE LOS DIPUTADOS";
		} else {
			colectivo = "EVENTUALES CONGRESO DE LOS DIPUTADOS";
		}

		texto = new Text(colectivo).setTextAlignment(TextAlignment.RIGHT).setFontSize(8);
		cabecera = new Paragraph().add(texto).setTextAlignment(TextAlignment.RIGHT).setPadding(0).setMargin(0)
		        .setMultipliedLeading(1).setMarginRight(10);
		doc.add(cabecera);

		final String modeloSolicitud = obtenerModeloSolicitud(solicitudBD);
		texto = new Text("MODELO " + modeloSolicitud).setTextAlignment(TextAlignment.RIGHT).setFontSize(8);
		cabecera = new Paragraph().add(texto).setPadding(0).setMargin(0).setMultipliedLeading(1).setMarginRight(12)
		        .setMarginBottom(20).setTextAlignment(TextAlignment.RIGHT);
		doc.add(cabecera);

		Table tabla = new Table(UnitValue.createPercentArray(new float[] {5f, 1f})).useAllAvailableWidth().setKeepTogether(true)
		        .setMarginBottom(10);

		String numeroSolicitud = "";
		if (Borrador) {
			numeroSolicitud = "[BORRADOR]";
		} else {
			numeroSolicitud = "" + solicitudBD.getNumero();
		}
		
		
		
		final String ejercicioNum =
		        "Ejercicio: " + solicitudBD.getEjercicio().toString() + ". Número solicitud: " + numeroSolicitud;
		if (solicitudBD.getTipo().equals("1") || solicitudBD.getTipo().equals("8")) {
			tabla.addCell(tituloTablaPrincipal("SOLICITUD DE PRESTACION DE SALUD", " (1) ",
			        ejercicioNum, 1, 2));
		} else if (solicitudBD.getTipo().equals("3")) {
			tabla.addCell(tituloTablaPrincipal("SOLICITUD DE PRESTACION CULTURAL PROFESIONAL -Artículo 9-", " (1) ", ejercicioNum,
			        1, 2));
		} else if (solicitudBD.getTipo().equals("4")) {
			tabla.addCell(tituloTablaPrincipal("SOLICITUD DE PRESTACION SOCIAL", "", ejercicioNum, 1, 2));
		} else if (solicitudBD.getTipo().equals("5")) {
			tabla.addCell(tituloTablaPrincipal("SOLICITUD DE PRESTACION PERSONAL", " (1) ", ejercicioNum, 1, 2));
		}


		tabla.addCell(subtipoTabla("DATOS DEL SOLICITANTE", 1, 1));

		tabla.addCell(celdaRellenar("Teléfono extensión", StringUtils.defaultString(solicitudBD.getTfno())));
		tabla.addCell(celdaRellenar("Apellidos/Nombre",
		        StringUtils.defaultString(solicitudBD.getApellidos() + " " + solicitudBD.getNombre())));
		tabla.addCell(celdaRellenar("N.I.F.", StringUtils.defaultString(solicitudBD.getDnipersona())));

		doc.add(tabla);

		Cell celda = new Cell();

		// El beneficiario sólo es para algunos tipos de prestaciones de salud
		if (solicitudBD.getTipo().equals("1") || solicitudBD.getTipo().equals("8")) {
			tabla = new Table(UnitValue.createPercentArray(new float[] {1f, 4f, 1f})).useAllAvailableWidth().setKeepTogether(true)
			        .setMarginBottom(10);

			celda = new Cell(2, 1);

			final Table tablacheck = new Table(UnitValue.createPercentArray(new float[] {0.5f, 1f})).useAllAvailableWidth()
			        .setKeepTogether(true).setBorder(Border.NO_BORDER);

			if (solicitudBD.getTipoBeneficiario().equals("TI")) {
				titularcasillaFuente(tablacheck, "Titular", "S");
			} else {
				titularcasillaFuente(tablacheck, "Titular", String.valueOf((char) 163));
			}

			if (solicitudBD.getTipoBeneficiario().equals("BE")) {
				titularcasillaFuente(tablacheck, "Familiar (2)", "S");
			} else {
				titularcasillaFuente(tablacheck, "Familiar (2)", String.valueOf((char) 163));
			}

			celda.add(tablacheck);

			tabla.addCell(celda);

			tabla.addCell(subtipoTabla("DATOS DEL BENEFICIARIO DE LA PRESTACIÓN", 1, 2));


			tabla.addCell(celdaRellenar("Apellidos/Nombre", solicitudBD.getNombrebeneficiariocompleto()));
			tabla.addCell(celdaRellenar("Parentesco con el titular", solicitudBD.getParentesco()));

			doc.add(tabla);
		}

		// Prestación de salud
		if (solicitudBD.getTipo().equals("1") || solicitudBD.getTipo().equals("8")) {
			if (solicitudBD.getTipo().equals("1")) {
				tabla = new Table(UnitValue.createPercentArray(new float[] {1f, 3f, 1f, 1f})).useAllAvailableWidth()
				        .setKeepTogether(true).setMarginBottom(10).setBorder(Border.NO_BORDER);
			} else {
				tabla = new Table(UnitValue.createPercentArray(new float[] {3f, 1f})).useAllAvailableWidth().setKeepTogether(true)
				        .setMarginBottom(10).setBorder(Border.NO_BORDER);
			}

			tabla.addCell(tituloTabla(StringUtils.defaultString(solicitudBD.getSubtipoformulario(),
			        solicitudBD.getSubtipoPrestacionesDescripcion()), " (3) ", 1, 4));

			if (solicitudBD.getTipo().equals("1")) {
				tabla.addCell(tituloColumnaConcepto("Codigo"));
			}
			tabla.addCell(tituloColumnaConcepto("Concepto"));
			if (solicitudBD.getTipo().equals("1")) {
				tabla.addCell(tituloColumnaConcepto("Cantidad"));
			}
			tabla.addCell(tituloColumnaConcepto("Importe"));

			Double total = 0.0;
			
			for (final ConceptoSolBean concepto : listaConceptos) {

				if (solicitudBD.getTipo().equals("1")) {
					tabla.addCell(textoColumnaConcepto(concepto.getCodConcepto()));
					tabla.addCell(textoColumnaConcepto(concepto.getDescConcepto()));
				} else {
					tabla.addCell(textoColumnaConceptoRellenar(concepto.getDescConcepto()));
				}

				if (solicitudBD.getTipo().equals("1")) {
					tabla.addCell(textoColumnaConceptoRellenar(concepto.getCantidad().toString()));
				}
				tabla.addCell(textoColumnaConceptoRellenar(concepto.getImporteSolicitadoSeparadorComa()));
		        
				total = total + concepto.getCantidad() * concepto.getImporteSolicitado();
			}

			tabla.addCell(celdaVacia(1, 2));
			tabla.addCell(textoColumnaConceptoCentrado("TOTAL"));
			final String totalString = String.format("%.2f", total).replace('.',',');
			tabla.addCell(textoColumnaConceptoRellenar(totalString));

			doc.add(tabla);
		}

		// Prestación cultural profesional
		else if (solicitudBD.getTipo().equals("3")) {

			tabla = new Table(UnitValue.createPercentArray(new float[] {1f, 3f, 1f, 1f, 2f})).useAllAvailableWidth()
			        .setKeepTogether(true).setMarginBottom(10).setBorder(Border.NO_BORDER);

			tabla.addCell(tituloColumnaConcepto("Codigo"));
			tabla.addCell(tituloColumnaConcepto("Concepto"));
			tabla.addCell(tituloColumnaConcepto("Importe"));
			tabla.addCell(tituloColumnaConcepto("Número de meses"));
			tabla.addCell(tituloColumnaConcepto("Especialización"));

			for (final ConceptoSolBean concepto : listaConceptos) {
				tabla.addCell(textoColumnaConcepto(concepto.getCodConcepto()));
				tabla.addCell(textoColumnaConcepto(concepto.getDescConcepto()));
				tabla.addCell(textoColumnaConcepto(concepto.getImporteSolicitadoSeparadorComa()));
				if (concepto.getMesesEstudio() != null) {
					tabla.addCell(textoColumnaConcepto(concepto.getMesesEstudio()));
				} else {
					tabla.addCell(textoColumnaConcepto(""));
				}
				if (concepto.getEspecializacion() != null) {
					tabla.addCell(textoColumnaConcepto(concepto.getEspecializacion()));
				} else {
					tabla.addCell(textoColumnaConcepto(""));
				}
			}

			doc.add(tabla);

		}


		// Prestación social (transporte)
		else if (solicitudBD.getTipo().equals("4")) {

			for (final ConceptoSolBean concepto : listaConceptos) {

				if (concepto.getCodConcepto().equals("403001")) {
					tabla = new Table(UnitValue.createPercentArray(new float[] {1f, 3f, 1f, 1f, 1f, 1f})).useAllAvailableWidth()
					        .setKeepTogether(true).setMarginBottom(10).setBorder(Border.NO_BORDER);

					tabla.addCell(tituloTabla(StringUtils.defaultString(solicitudBD.getSubtipoformulario(),
					        solicitudBD.getSubtipoPrestacionesDescripcion()), "", 1, 6));

					tabla.addCell(tituloColumnaConcepto("Codigo"));
					tabla.addCell(tituloColumnaConcepto("Concepto"));
					tabla.addCell(tituloColumnaConcepto("Año"));
					tabla.addCell(tituloColumnaConcepto("Zona"));
					tabla.addCell(tituloColumnaConcepto("Número meses"));
					tabla.addCell(tituloColumnaConcepto("Importe"));

					tabla.addCell(textoColumnaConcepto(concepto.getCodConcepto()));
					tabla.addCell(textoColumnaConcepto(concepto.getDescConcepto()));
					tabla.addCell(textoColumnaConceptoRellenar(concepto.getAnioTransporte().toString()));
					tabla.addCell(textoColumnaConceptoRellenar(concepto.getZonaTransporte().toString()));
					tabla.addCell(textoColumnaConceptoRellenar(concepto.getMesesTransporte().toString()));
					tabla.addCell(textoColumnaConceptoRellenar(concepto.getImporteSolicitadoSeparadorComa()));
				} else {
					tabla = new Table(UnitValue.createPercentArray(new float[] {1f, 4f, 1f, 1f, 1f})).useAllAvailableWidth()
					        .setKeepTogether(true).setMarginBottom(10).setBorder(Border.NO_BORDER);

					tabla.addCell(tituloTabla(StringUtils.defaultString(solicitudBD.getSubtipoformulario(),
					        solicitudBD.getSubtipoPrestacionesDescripcion()), "", 1, 5));

					tabla.addCell(tituloColumnaConcepto("Codigo"));
					tabla.addCell(tituloColumnaConcepto("Concepto"));
					tabla.addCell(tituloColumnaConcepto("Año"));
					tabla.addCell(tituloColumnaConcepto("Zona"));
					tabla.addCell(tituloColumnaConcepto("Importe"));

					tabla.addCell(textoColumnaConcepto(concepto.getCodConcepto()));
					tabla.addCell(textoColumnaConcepto(concepto.getDescConcepto()));
					tabla.addCell(textoColumnaConceptoRellenar(concepto.getAnioTransporte().toString()));
					tabla.addCell(textoColumnaConceptoRellenar(concepto.getZonaTransporte()));
					tabla.addCell(textoColumnaConceptoRellenar(concepto.getImporteSolicitadoSeparadorComa()));
				}
			}

			doc.add(tabla);
		}

		// Personales
		else if (solicitudBD.getTipo().equals("5")) {

			// Incapacidad transitoria
			if (solicitudBD.getSubtipo().equals("06")) {
				final ConceptoSolBean concepto = listaConceptos.get(0);

				tabla = new Table(UnitValue.createPercentArray(new float[] {1f, 3f})).useAllAvailableWidth().setKeepTogether(true)
				        .setMarginBottom(10).setBorder(Border.NO_BORDER);
				tabla.addCell(tituloColumnaConcepto("Codigo"));
				tabla.addCell(tituloColumnaConcepto("Concepto"));
				tabla.addCell(textoColumnaConcepto(concepto.getCodConcepto()));
				tabla.addCell(textoColumnaConcepto(concepto.getDescConcepto()));
				doc.add(tabla);

				tabla = new Table(UnitValue.createPercentArray(new float[] {1.5f, 1.5f, 1f})).useAllAvailableWidth()
				        .setKeepTogether(true).setMarginBottom(10).setBorder(Border.NO_BORDER);
				tabla.addCell(textoColumnaConceptoRellenar("Fecha de la primera licencia por enfermedad"));
				tabla.addCell(textoColumnaConceptoRellenar("Periodo para el que se solicita la ayuda (2)"));
				tabla.addCell(textoColumnaConceptoRellenar("Importe"));
				final SimpleDateFormat sd = new SimpleDateFormat("dd/MM/yyyy");
				final String textoFecha = sd.format(concepto.getFechaPrimeraEnfermedad());
				tabla.addCell(textoColumnaConceptoRellenar(textoFecha));
				tabla.addCell(textoColumnaConceptoRellenar(concepto.getPeriodoAyuda()));
				tabla.addCell(textoColumnaConceptoRellenar(concepto.getImporteSolicitadoSeparadorComa()));

			}
			// Estudios de los hijos
			else if (solicitudBD.getSubtipo().equals("07")) {
				tabla = new Table(UnitValue.createPercentArray(new float[] {3f, 1f})).useAllAvailableWidth().setKeepTogether(true)
				        .setMarginBottom(10).setBorder(Border.NO_BORDER);

				tabla.addCell(tituloTabla(StringUtils.defaultString(solicitudBD.getSubtipoformulario(),
				        solicitudBD.getSubtipoPrestacionesDescripcion()), " (2) ", 1, 2));
				tabla.addCell(subtipoTabla("DATOS DEL BENEFICIARIO PARA EL QUE SE SOLICITA LA AYUDA (3)", 1, 2));

				tabla.addCell(celdaRellenar("Apellidos/Nombre",
				        StringUtils.defaultString(solicitudBD.getNombrebeneficiariocompleto())));
				final SimpleDateFormat sd = new SimpleDateFormat("dd/MM/yyyy");
				final String textoFecha = sd.format(solicitudBD.getFechaNacBenef());
				tabla.addCell(celdaRellenar("Fecha de nacimiento", StringUtils.defaultString(textoFecha)));
				doc.add(tabla);

				tabla = new Table(UnitValue.createPercentArray(new float[] {2f, 0.7f, 2f, 1f})).useAllAvailableWidth()
				        .setKeepTogether(true).setMarginBottom(10).setBorder(Border.NO_BORDER);

				for (final ConceptoSolBean concepto : listaConceptos) {
					tabla.addCell(celdaRellenar("Etapa de Estudios (4)", StringUtils.defaultString(concepto.getDescConcepto())));
					tabla.addCell(celdaRellenar("Período académico", StringUtils.defaultString(concepto.getPeriodoAcademico())));
					tabla.addCell(
					        celdaRellenar("Denominación del centro", StringUtils.defaultString(concepto.getCentroEstudios())));
					tabla.addCell(celdaRellenar("Carácter (5)", StringUtils.defaultString(concepto.getCaracter())));
				}

			} else {

				final ConceptoSolBean primerConcepto = listaConceptos.get(0);

				// Jubilaciones
				if (        primerConcepto.getCodConcepto().equals("505001") 
						|| primerConcepto.getCodConcepto().equals(PRESTACION_NORMAL) 
						|| primerConcepto.getCodConcepto().equals(PRESTACION_ESPECIAL) ){
					tabla = new Table(UnitValue.createPercentArray(new float[] {1f, 3f})).useAllAvailableWidth()
					        .setKeepTogether(true).setMarginBottom(10).setBorder(Border.NO_BORDER);
					tabla.addCell(tituloColumnaConcepto("Codigo"));
					tabla.addCell(tituloColumnaConcepto("Concepto"));

					for (final ConceptoSolBean concepto : listaConceptos) {
						tabla.addCell(textoColumnaConcepto(concepto.getCodConcepto()));
						tabla.addCell(textoColumnaConcepto(concepto.getDescConcepto()));
					}
				} else {
					tabla = new Table(UnitValue.createPercentArray(new float[] {1f, 3f, 1f})).useAllAvailableWidth()
					        .setKeepTogether(true).setMarginBottom(10).setBorder(Border.NO_BORDER);

					tabla.addCell(tituloColumnaConcepto("Codigo"));
					tabla.addCell(tituloColumnaConcepto("Concepto"));
					tabla.addCell(tituloColumnaConcepto("Importe"));

					for (final ConceptoSolBean concepto : listaConceptos) {

						tabla.addCell(textoColumnaConcepto(concepto.getCodConcepto()));
						tabla.addCell(textoColumnaConcepto(concepto.getDescConcepto()));
						tabla.addCell(textoColumnaConceptoRellenar(concepto.getImporteSolicitadoSeparadorComa()));
					}
				}
			}

			doc.add(tabla);
		}

		tabla = new Table(UnitValue.createPercentArray(new float[] {1f, 1f, 2f})).useAllAvailableWidth().setKeepTogether(true)
		        .setMarginBottom(10).setBorder(Border.NO_BORDER);

		tabla.addCell(celdaVacia(1, 1));

		final SimpleDateFormat sd = new SimpleDateFormat("dd/MM/yyyy");

		String textoFecha = "";
		if (solicitudBD.getFechaSolicitud() != null) {
			textoFecha = sd.format(solicitudBD.getFechaSolicitud());
		}

		tabla.addCell(tablaEnCelda("Fecha: " + textoFecha, colorFondo));
		tabla.addCell(tablaEnCelda(
		        "El solicitante se responsabiliza de la veracidad de los datos contenidos en esta solicitud, así como de la documentación aportada, siendo fiel reflejo de su original. Dichos originales se facilitarán al Departamento de Acción Social si le fueran requeridos.",
		        null));

		doc.add(tabla);

		texto = new Text("DOCUMENTACIÓN APORTADA:").setUnderline();
		cabecera = new Paragraph().add(texto).setTextAlignment(TextAlignment.LEFT).setPadding(0).setMargin(0)
		        .setMultipliedLeading(1).setMarginBottom(10);
		doc.add(cabecera);

		for (final DocumentoSolBean documento : listaDocumentos) {

			Paragraph parrafo = new Paragraph().setMultipliedLeading(1);
			final PdfFont zapfdingbats = PdfFontFactory.createFont(StandardFonts.ZAPFDINGBATS);
			texto = new Text("5 ").setFont(zapfdingbats);
			parrafo.add(texto);

			texto = new Text(documento.getTipoDocDescripcion().toUpperCase());
			parrafo.add(texto);
			doc.add(parrafo);

			if (documento.getTipoDoc().equals("1")) {

				texto = new Text("Emisor ");
				parrafo = new Paragraph().add(texto).setMultipliedLeading(1).setMargin(0).setPadding(0);

				texto = new Text(StringUtils.defaultString(documento.getEmisorFactura(), "           ")).setFontSize(10)
				        .setUnderline();
				parrafo.add(texto);

				texto = new Text(" Fecha ");
				parrafo.add(texto);

				String textoStr = "         ";
				if (documento.getFechaFactura() != null) {

					textoStr = sd.format(documento.getFechaFactura());

				}
				texto = new Text(textoStr).setFontSize(10).setUnderline();
				parrafo.add(texto);

				texto = new Text(" Importe ");
				parrafo.add(texto);

				textoStr = "         ";
				if (documento.getImporteFactura() != null) {
					textoStr = documento.getImporteFacturaSeparadoComas();
				}

				texto = new Text(textoStr).setFontSize(10).setUnderline();
				parrafo.add(texto);

				doc.add(parrafo);
			}

		}

		tabla = new Table(UnitValue.createPercentArray(new float[] {1f})).useAllAvailableWidth().setKeepTogether(true)
		        .setMarginBottom(10).setBorder(Border.NO_BORDER).setMarginTop(10);

		celda = new Cell();
		texto = new Text(textoNota).setFontSize(8);;
		Paragraph parrafo = new Paragraph().add(texto).setMargin(0).setPadding(0).setMultipliedLeading(1);
		celda.add(parrafo);
		tabla.addCell(celda);
		doc.add(tabla);

		texto = new Text(html2text(proteccionDatos)).setFontSize(8);;
		parrafo = new Paragraph().add(texto).setMargin(0).setPadding(0).setMultipliedLeading(1);
		doc.add(parrafo);
		doc.close();

		dataStreamForPdf = (new ByteArrayInputStream(outputStream.toByteArray()));

		logger.debug("Sale de generarPDFSolicitud");

		return dataStreamForPdf;
	}

	private String obtenerModeloSolicitud(final SolicitudBean solicitudBD) {
		String modelo = solicitudBD.getColectivo().concat(".");

		if (solicitudBD.getTipo().equals("1")) {
			modelo = modelo.concat("CS1.").concat(solicitudBD.getSubtipo());
		} else if (solicitudBD.getTipo().equals("3")) {
			modelo = modelo.concat("CP3");
		} else if (solicitudBD.getTipo().equals("4")) {
			modelo = modelo.concat("S4.").concat(solicitudBD.getSubtipo());
		} else if (solicitudBD.getTipo().equals("5")) {
			modelo = modelo.concat("P5.").concat(solicitudBD.getSubtipo());
		} else if (solicitudBD.getTipo().equals("8")) {
			modelo = modelo.concat("CS8");
		}
		return modelo;
	}

	private Cell tablaEnCelda(final String textoStr, final Color colorFondo) {
		final Table tabla = new Table(UnitValue.createPercentArray(new float[] {1f})).useAllAvailableWidth().setKeepTogether(true)
		        .setMarginBottom(10).setBorder(Border.NO_BORDER).setMargin(5);

		final Cell celda = new Cell().setTextAlignment(TextAlignment.LEFT).setPadding(10);

		if (colorFondo != null) {
			celda.setBackgroundColor(colorFondo);
		}

		final Text texto = new Text(textoStr).setFontSize(10);
		final Paragraph parrafo = new Paragraph().add(texto).setMultipliedLeading(1);

		celda.add(parrafo);

		tabla.addCell(celda);

		return new Cell().add(tabla).setBorder(Border.NO_BORDER);

	}

	private String formatearFecha(final Date fecha) {
		final DateFormat dateFormat = new SimpleDateFormat("dd/MM/yyyy");
		final String strDate = dateFormat.format(fecha);
		return strDate;
	}

	private Cell celdaRellenar(final String titulo, final String contenido) {

		final Color colorFondo = new DeviceRgb(255, 255, 205);

		final Cell celda = new Cell().setTextAlignment(TextAlignment.LEFT).setPaddingLeft(2).setBackgroundColor(colorFondo);

		if (StringUtils.isNotBlank(titulo)) {
			final Text texto = new Text(titulo).setFontSize(6);
			final Paragraph parrafo = new Paragraph().add(texto);
			celda.add(parrafo);
		}
		final Text texto = new Text(contenido).setFontSize(10);
		final Paragraph parrafo = new Paragraph().add(texto);

		celda.add(parrafo);

		return celda;
	}

	private Cell tituloTabla(final String textoTitulo, final String otroTexto, final Integer filas, final Integer columnas) {
		Text texto = new Text(textoTitulo).setBold().setFontSize(12);
		final Paragraph parrafo = new Paragraph().add(texto);

		texto = new Text(otroTexto);
		parrafo.add(texto);

		final Cell celda = new Cell(filas, columnas).add(parrafo).setTextAlignment(TextAlignment.CENTER)
		        .setVerticalAlignment(VerticalAlignment.MIDDLE);

		return celda;
	}

	private Cell tituloTablaPrincipal(final String textoTitulo, final String otroTexto, final String subTexto,
	        final Integer filas, final Integer columnas) {
		Text texto = new Text(textoTitulo).setBold().setFontSize(12);
		final Paragraph parrafo = new Paragraph().add(texto);

		texto = new Text(otroTexto);
		parrafo.add(texto);

		parrafo.add("\n");

		texto = new Text(subTexto).setBold().setFontSize(10);
		parrafo.add(texto);

		final Cell celda = new Cell(filas, columnas).add(parrafo).setTextAlignment(TextAlignment.CENTER)
		        .setVerticalAlignment(VerticalAlignment.MIDDLE);

		return celda;
	}

	private Cell subtipoTabla(final String textoSub, final int filas, final int columnas) {
		final Text texto = new Text(textoSub).setFontSize(10);
		final Paragraph parrafo = new Paragraph().add(texto);

		final Cell celda = new Cell(filas, columnas).add(parrafo).setTextAlignment(TextAlignment.CENTER)
		        .setVerticalAlignment(VerticalAlignment.MIDDLE);

		return celda;
	}

	private Cell tituloColumnaConcepto(final String tituloCol) {
		final Text texto = new Text(tituloCol).setFontSize(10).setItalic();
		final Paragraph parrafo = new Paragraph().add(texto);

		final Cell celda = new Cell().add(parrafo).setTextAlignment(TextAlignment.LEFT)
		        .setVerticalAlignment(VerticalAlignment.MIDDLE).setPadding(2);

		return celda;
	}

	private Cell textoColumnaConcepto(final String tituloCol) {
		final Text texto = new Text(tituloCol).setFontSize(10);
		final Paragraph parrafo = new Paragraph().add(texto);

		final Cell celda = new Cell().add(parrafo).setTextAlignment(TextAlignment.LEFT)
		        .setVerticalAlignment(VerticalAlignment.MIDDLE).setPadding(2);

		return celda;
	}

	private Cell textoColumnaConceptoCentrado(final String tituloCol) {
		final Text texto = new Text(tituloCol).setFontSize(10);
		final Paragraph parrafo = new Paragraph().add(texto);

		final Cell celda = new Cell().add(parrafo).setTextAlignment(TextAlignment.CENTER)
		        .setVerticalAlignment(VerticalAlignment.MIDDLE).setPadding(2);

		return celda;
	}

	private Cell textoColumnaConceptoRellenar(final String tituloCol) {

		final Color colorFondo = new DeviceRgb(255, 255, 205);


		final Text texto = new Text(tituloCol).setFontSize(10);
		final Paragraph parrafo = new Paragraph().add(texto);

		final Cell celda = new Cell().add(parrafo).setTextAlignment(TextAlignment.RIGHT)
		        .setVerticalAlignment(VerticalAlignment.MIDDLE).setPadding(2).setPaddingRight(20).setBackgroundColor(colorFondo);

		return celda;
	}

	private Cell celdaVacia(final int fila, final int columna) {
		final Text texto = new Text("");
		final Paragraph parrafo = new Paragraph().add(texto);

		final Cell celda = new Cell(fila, columna).add(parrafo).setPadding(2).setBorder(Border.NO_BORDER);

		return celda;
	}

	private void titularcasillaFuente(final Table tabla, final String titulo, final String opcion) throws IOException {

		final FontProgram fontProgram =
		        FontProgramFactory.createFont("/archivos2/aplicaciones/accionSocial/recursos/WINGDNG2.TTF");

		final PdfFont fontWing2 = PdfFontFactory.createFont(fontProgram, PdfEncodings.WINANSI, true);

		Cell celdacheck =
		        new Cell().setBorder(Border.NO_BORDER).setVerticalAlignment(VerticalAlignment.MIDDLE).setBorder(Border.NO_BORDER);

		final Text tCasilla = new Text(opcion).setFont(fontWing2).setFontSize(16);

		Paragraph parrafo = new Paragraph().add(tCasilla).setPadding(0);
		celdacheck.add(parrafo);
		tabla.addCell(celdacheck);
		parrafo = new Paragraph().add(titulo).setPadding(0);
		celdacheck = new Cell().add(parrafo).setVerticalAlignment(VerticalAlignment.MIDDLE).setBorder(Border.NO_BORDER);
		tabla.addCell(celdacheck);
	}


//	private void titularcasilla(final Table tabla, final String titulo, final Image imagen) {
//
//		Cell celdacheck = new Cell().setBorder(Border.NO_BORDER);
//
//		Paragraph parrafo = new Paragraph().add(imagen).setPadding(0);
//		celdacheck.add(parrafo);
//		tabla.addCell(celdacheck);
//		parrafo = new Paragraph().add(titulo);
//		celdacheck = new Cell().add(parrafo).setVerticalAlignment(VerticalAlignment.MIDDLE).setBorder(Border.NO_BORDER);
//		tabla.addCell(celdacheck);
//	}

	private static class SolicitudEventHandler implements IEventHandler {
		protected Document doc;

		public SolicitudEventHandler(final Document doc) {
			this.doc = doc;
		}

		@Override
		public void handleEvent(final Event currentEvent) {
			final PdfDocumentEvent docEvent = (PdfDocumentEvent) currentEvent;
			final Rectangle pageSize = docEvent.getPage().getPageSize();
			PdfFont font = null;
			try {
				font = PdfFontFactory.createFont(StandardFonts.TIMES_ROMAN);
			} catch (final Exception e) {
			}


			Canvas canvas = new Canvas(docEvent.getPage(), pageSize);


			canvas = new Canvas(docEvent.getPage(), pageSize);


			final float footerX = doc.getLeftMargin();
			final float footerY = doc.getBottomMargin() - 10f;

			final Text texto = new Text(
			        "DIRECCION DE RECURSOS HUMANOS Y GOBIERNO INTERIOR DE LA SECRETARIA GENERAL DEL CONGRESO DE LOS DIPUTADOS.\r\n"
			                + "DEPARTAMENTO DE ACCION SOCIAL");

			final Paragraph pie = new Paragraph(texto).setHorizontalAlignment(HorizontalAlignment.LEFT);

			canvas = new Canvas(docEvent.getPage(), pageSize);
			canvas.setFont(font).setFontSize(8).showTextAligned(pie, footerX, footerY, TextAlignment.LEFT).close();

		}
	}

	public Cell anadirCabeceraTabla(final String texto, final String alin, final Integer columnas) {
		final Paragraph paragraphBold = new Paragraph().add(texto).setBold();

		if (alin.equals("I")) {
			return new Cell(1, columnas).add(paragraphBold).setMarginBottom(5f * 2).setTextAlignment(TextAlignment.LEFT)
			        .setBorder(Border.NO_BORDER);
		} else if (alin.equals("C")) {
			return new Cell(1, columnas).add(paragraphBold).setMarginBottom(5f * 2).setTextAlignment(TextAlignment.CENTER)
			        .setBorder(Border.NO_BORDER);
		} else {
			return new Cell(1, columnas).add(paragraphBold).setMarginBottom(5f * 2).setTextAlignment(TextAlignment.RIGHT)
			        .setBorder(Border.NO_BORDER);
		}
	}

	public Cell anadirDatoTabla(final String texto, final Color color, final String alin) {
		final Paragraph paragraph = new Paragraph().add(StringUtils.defaultString(texto));

		if (color != null) {
			paragraph.setFontColor(color).setUnderline();
		}

		if (alin.equals("I")) {
			return new Cell().add(paragraph).setMargin(0).setTextAlignment(TextAlignment.LEFT).setBorder(Border.NO_BORDER);
		} else if (alin.equals("C")) {
			return new Cell().add(paragraph).setMargin(0).setTextAlignment(TextAlignment.CENTER).setBorder(Border.NO_BORDER);
		} else {
			return new Cell().add(paragraph).setMargin(0).setTextAlignment(TextAlignment.RIGHT).setBorder(Border.NO_BORDER);
		}
	}

	@Override
	public InputStream generarPDFListadoCompleto(final BusquedaSolBean busqueda) throws Exception {
		logger.debug("Entrando en generarPDFListado");

		InputStream dataStreamForPdf = null;

		final int totalSolicitudesConsulta = solicitudesPrestacionesDao.getNumRegSolicitudes(busqueda);
		final List<SolicitudBean> listaSolicitudes =
		        solicitudesPrestacionesDao.getListaSolicitudes(0, totalSolicitudesConsulta, busqueda);

		final ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
		final PdfDocument pdfDoc = new PdfDocument(new PdfWriter(outputStream));
		final Document doc = new Document(pdfDoc, PageSize.A4);

		final ListadoSolEventHandler listadoSolEventHandler = new ListadoSolEventHandler(doc);
		pdfDoc.addEventHandler(PdfDocumentEvent.END_PAGE, listadoSolEventHandler);

		doc.setMargins(80, 10, 40, 10);

		final PdfFont font = PdfFontFactory.createFont(StandardFonts.TIMES_ROMAN);
		doc.setFont(font);
		doc.setFontSize(9);
		doc.setTextAlignment(TextAlignment.JUSTIFIED);

		final float[] columnWidths = {0.3f, 0.5f, 1f, 0.4f, 1f, 0.4f, 0.4f, 0.5f, 0.7f};

		final Table table = new Table(UnitValue.createPercentArray(columnWidths)).useAllAvailableWidth().setMarginTop(5f);

		table.addHeaderCell(anadirCabeceraTabla("Solicitud", "C", 1));

		table.addHeaderCell(anadirCabeceraTabla("Fecha", "I", 1));
		table.addHeaderCell(anadirCabeceraTabla("Titular", "C", 2));

		table.addHeaderCell(anadirCabeceraTabla("Beneficiario", "C", 2));

		table.addHeaderCell(anadirCabeceraTabla("Clase Benef.", "C", 1));
		
		table.addHeaderCell(anadirCabeceraTabla("Prestación", "C", 1));

		table.addHeaderCell(anadirCabeceraTabla("Estado", "C", 1));

		for (final SolicitudBean solicitud : listaSolicitudes) {

			final Color color = null;

			table.addCell(
			        anadirDatoTabla(solicitud.getEjercicio().toString() + "/" + solicitud.getNumero().toString(), color, "I"));
			table.addCell(anadirDatoTabla(formatearFecha(solicitud.getFechaSolicitud()), color, "I"));
			table.addCell(anadirDatoTabla(solicitud.getNombrepersonacompleto(), color, "I"));
			table.addCell(anadirDatoTabla(solicitud.getDnipersona(), color, "I"));
			table.addCell(anadirDatoTabla(solicitud.getNombrebeneficiariocompleto(), color, "I"));
			table.addCell(anadirDatoTabla(solicitud.getDnibeneficiario(), color, "I"));
			table.addCell(anadirDatoTabla(solicitud.getTipoBeneficiarioToString(), color, "I"));
			table.addCell(anadirDatoTabla(solicitud.getSubtipoPrestacionesDescripcion(), color, "I"));
			table.addCell(anadirDatoTabla(solicitud.getEstadodescripciongestion(), color, "I"));
		}

		doc.add(table);

		doc.close();

		dataStreamForPdf = (new ByteArrayInputStream(outputStream.toByteArray()));

		logger.debug("Sale de generarPDFListado");

		return dataStreamForPdf;

	}

	private static class ListadoSolEventHandler implements IEventHandler {
		protected Document doc;

		public ListadoSolEventHandler(final Document doc) {
			this.doc = doc;
		}

		@Override
		public void handleEvent(final Event currentEvent) {
			final PdfDocumentEvent docEvent = (PdfDocumentEvent) currentEvent;
			final Rectangle pageSize = docEvent.getPage().getPageSize();
			PdfFont font = null;
			try {
				font = PdfFontFactory.createFont(StandardFonts.TIMES_ROMAN);
			} catch (final IOException e) {
				logger.error(e);
			} catch (final Exception e) {
				logger.error(e);
			}

			final String texto = "Listado de Solicitudes presentadas";

			final float headerX = doc.getPdfDocument().getDefaultPageSize().getWidth() / 2;
			final float headerY = doc.getPdfDocument().getDefaultPageSize().getHeight() - doc.getTopMargin() + 30f;

			final Paragraph cabecera = new Paragraph(texto).setHorizontalAlignment(HorizontalAlignment.CENTER);

			Canvas canvas = new Canvas(docEvent.getPage(), pageSize);
			canvas.setFont(font).setFontSize(14).setBold().showTextAligned(cabecera, headerX, headerY, TextAlignment.CENTER)
			        .close();

			// Fecha y hora de generación del documento a pie de página
			final Date hoy = new Date();
			final SimpleDateFormat s = new SimpleDateFormat("dd/MM/yyyy HH:mm:ss");
			final String fecha = "Fecha y hora de generación del documento: " + s.format(hoy);

			final float footerX = doc.getLeftMargin();
			final float footerY = doc.getBottomMargin() - 10f;

			final Paragraph pie = new Paragraph(fecha).setHorizontalAlignment(HorizontalAlignment.LEFT);

			canvas = new Canvas(docEvent.getPage(), pageSize);
			canvas.setFont(font).setFontSize(11).setBold().showTextAligned(pie, footerX, footerY, TextAlignment.LEFT).close();

		}
	}

	@Override
	public InputStream generarEscritoDenegacionDirectoraJefa(final ConceptoSolBean conceptoGenerar) {
		logger.debug("Entrando en generarEscritoDenegacionDirectoraJefa");

		InputStream dataStreamForPdf = null;

		SolicitudBean solicitud = new SolicitudBean();
		solicitud.setNumero(conceptoGenerar.getNumero());
		solicitud.setEjercicio(conceptoGenerar.getEjercicio());
		solicitud = solicitudesPrestacionesDao.getSolicitud(solicitud);
		final ConceptoSolBean concepto = solicitudesPrestacionesDao.getConceptoSolicitud(conceptoGenerar);
		final FirmaEscritoBean firma = solicitudesPrestacionesDao.getInfoFirmaEscrito(1);
		final ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
		final PdfDocument pdfDoc = new PdfDocument(new PdfWriter(outputStream));
		final Document doc = new Document(pdfDoc, PageSize.A4);

		doc.setMargins(20, 60, 30, 60);
		doc.setFontSize(11);


		// Añadimos el escudo

		try {
			final byte[] imagenBytes = solicitudesPrestacionesDao.getEscudoEscrito(solicitud.getColectivo());
			final ImageData imageData = ImageDataFactory.create(imagenBytes);
			final Image imagen = new Image(imageData);
			imagen.scale(0.1f, 0.1f).setPadding(0).setMarginLeft(70);
			doc.add(imagen);

		} catch (final Exception e) {
			logger.error(e.getMessage());
		}

		Text texto = new Text(firma.getDireccionRemite()).setFontSize(8);
		Paragraph parrafo = new Paragraph().add(texto).setMargin(0).setPadding(0)
		        .setHorizontalAlignment(HorizontalAlignment.CENTER).setMultipliedLeading(1).setMarginLeft(20).setMarginTop(5);
		doc.add(parrafo);

		// El texto de la carta

		texto = new Text("En el día de hoy he dictado la siguiente resolución:");

		parrafo = new Paragraph().add(texto).setMargin(0).setPadding(0).setMultipliedLeading(1).setMarginLeft(40)
		        .setFirstLineIndent(70).setMarginTop(90).setTextAlignment(TextAlignment.JUSTIFIED);
		doc.add(parrafo);

		final SimpleDateFormat sp = new SimpleDateFormat("dd 'de' MMMMM 'de' yyyy");

		texto = new Text("\"EXAMINADA la solicitud de fecha " + sp.format(solicitud.getFechaSolicitud()) + ", de "
		        + solicitud.getNombre() + " " + solicitud.getApellidos() + ", de prestación "
		        + solicitud.getTipoPrestacionDescripcion() + ", en concepto de " + concepto.getDescConcepto() + ",");
		parrafo = new Paragraph().add(texto).setMargin(0).setPadding(0).setMultipliedLeading(1).setMarginLeft(40)
		        .setFirstLineIndent(70).setMarginTop(20).setTextAlignment(TextAlignment.JUSTIFIED);
		doc.add(parrafo);

		texto = new Text("VISTO el vigente Reglamento del Fondo de Prestaciones Sociales de los " + solicitud.getDesColectivo()
		        + " de 18 de julio de 1991,");
		parrafo = new Paragraph().add(texto).setMargin(0).setPadding(0).setMultipliedLeading(1).setMarginLeft(40)
		        .setFirstLineIndent(70).setMarginTop(20).setTextAlignment(TextAlignment.JUSTIFIED);
		doc.add(parrafo);

		texto = new Text("VISTOS los antecedentes que obran en el Departamento de Acción Social de esta Dirección,");
		parrafo = new Paragraph().add(texto).setMargin(0).setPadding(0).setMultipliedLeading(1).setMarginLeft(40)
		        .setFirstLineIndent(70).setMarginTop(20).setTextAlignment(TextAlignment.JUSTIFIED);
		doc.add(parrafo);

		texto = new Text("PROCEDE denegar la prestación solicitada, por el motivo siguiente:");
		parrafo = new Paragraph().add(texto).setMargin(0).setPadding(0).setMultipliedLeading(1).setMarginLeft(40)
		        .setFirstLineIndent(70).setMarginTop(20).setTextAlignment(TextAlignment.JUSTIFIED);
		doc.add(parrafo);

		texto = new Text(concepto.getDescMotivoDenegacion() + "\"");
		parrafo = new Paragraph().add(texto).setMargin(0).setPadding(0).setMultipliedLeading(1).setMarginLeft(40)
		        .setFirstLineIndent(70).setMarginTop(20).setTextAlignment(TextAlignment.JUSTIFIED);
		doc.add(parrafo);

		texto = new Text("Lo que le comunico para su notificación al interesado.");
		parrafo = new Paragraph().add(texto).setMargin(0).setPadding(0).setMultipliedLeading(1).setMarginLeft(40)
		        .setFirstLineIndent(70).setMarginTop(20);
		doc.add(parrafo);

		texto = new Text("Palacio del Congreso de los Diputados, a " + sp.format(solicitud.getFechaSolicitud()) + ".");
		parrafo = new Paragraph().add(texto).setMargin(0).setPadding(0).setMultipliedLeading(1).setMarginLeft(40)
		        .setFirstLineIndent(70).setMarginTop(20).setTextAlignment(TextAlignment.JUSTIFIED);
		doc.add(parrafo);

		texto = new Text(firma.getNombreRemite());
		parrafo = new Paragraph().add(texto).setMargin(0).setPadding(0).setMultipliedLeading(1).setMarginLeft(0)
		        .setFirstLineIndent(70).setMarginTop(60).setTextAlignment(TextAlignment.CENTER);
		doc.add(parrafo);

		texto = new Text(firma.getCargoRemite());
		parrafo = new Paragraph().add(texto).setMargin(0).setPadding(0).setMultipliedLeading(1).setMarginLeft(0)
		        .setFirstLineIndent(70).setMarginTop(0).setTextAlignment(TextAlignment.CENTER);
		doc.add(parrafo);

		if (concepto.getEstado() == 90) {
			try {
				final byte[] imagenBytes = solicitudesPrestacionesDao.getImagenFirma(1);
				final ImageData imageData = ImageDataFactory.create(imagenBytes);
				final Image imagen = new Image(imageData);
				imagen.scale(0.2f, 0.2f).setPadding(0).setMarginLeft(200).setMarginTop(10);
				doc.add(imagen);

			} catch (final Exception e) {
				logger.error(e.getMessage());
			}
		}

		texto = new Text(firma.getTratamientoDestino() + " " + firma.getNombreDestino());
		parrafo = new Paragraph().add(texto).setMargin(0).setPadding(0).setMultipliedLeading(1).setFixedPosition(60, 50, 800);
		doc.add(parrafo);

		texto = new Text(firma.getCargoDestino());
		parrafo = new Paragraph().add(texto).setMargin(0).setPadding(0).setMultipliedLeading(1).setFixedPosition(60, 30, 800);
		doc.add(parrafo);

		doc.close();

		dataStreamForPdf = (new ByteArrayInputStream(outputStream.toByteArray()));

		logger.debug("Sale de generarPDFListado");

		return dataStreamForPdf;

	}

	@Override
	public InputStream generarEscritoDenegacionJefaInteresado(final ConceptoSolBean conceptoGenerar) {
		logger.debug("Entrando en generarEscritoDenegacionDirectoraJefa");

		InputStream dataStreamForPdf = null;

		SolicitudBean solicitud = new SolicitudBean();
		solicitud.setNumero(conceptoGenerar.getNumero());
		solicitud.setEjercicio(conceptoGenerar.getEjercicio());
		solicitud = solicitudesPrestacionesDao.getSolicitud(solicitud);
		final ConceptoSolBean concepto = solicitudesPrestacionesDao.getConceptoSolicitud(conceptoGenerar);
		final FirmaEscritoBean firma = solicitudesPrestacionesDao.getInfoFirmaEscrito(2);
		final ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
		final PdfDocument pdfDoc = new PdfDocument(new PdfWriter(outputStream));
		final Document doc = new Document(pdfDoc, PageSize.A4);

		doc.setMargins(20, 60, 30, 60);
		doc.setFontSize(11);


		// Añadimos el escudo
		try {
			ImageData imageData;
			final byte[] imagenBytes = solicitudesPrestacionesDao.getEscudoEscrito(solicitud.getColectivo());
			imageData = ImageDataFactory.create(imagenBytes);
			final Image imagen = new Image(imageData);
			imagen.scale(0.1f, 0.1f).setPadding(0).setMarginLeft(70);
			doc.add(imagen);

		} catch (final Exception e) {
			logger.error(e.getMessage());
		}

		Text texto = new Text(firma.getDireccionRemite()).setFontSize(8);
		Paragraph parrafo = new Paragraph().add(texto).setMargin(0).setPadding(0)
		        .setHorizontalAlignment(HorizontalAlignment.CENTER).setMultipliedLeading(1).setMarginLeft(20).setMarginTop(5);
		doc.add(parrafo);

		// El texto de la carta

		texto = new Text("La " + firma.getTratamientoRemiteSup() + " " + firma.getCargoRemiteSup()
		        + " de la Secretaría General del Congreso de los Diputados ha dictado en el día de hoy la siguiente resolución:");

		parrafo = new Paragraph().add(texto).setMargin(0).setPadding(0).setMultipliedLeading(1).setMarginLeft(40)
		        .setFirstLineIndent(70).setMarginTop(80).setTextAlignment(TextAlignment.JUSTIFIED);
		doc.add(parrafo);

		final SimpleDateFormat sp = new SimpleDateFormat("dd 'de' MMMMM 'de' yyyy");

		texto = new Text("\"EXAMINADA la solicitud de fecha " + sp.format(solicitud.getFechaSolicitud()) + ", de "
		        + solicitud.getNombre() + " " + solicitud.getApellidos() + ", de prestación "
		        + solicitud.getTipoPrestacionDescripcion() + ", en concepto de " + concepto.getDescConcepto() + ",");
		parrafo = new Paragraph().add(texto).setMargin(0).setPadding(0).setMultipliedLeading(1).setMarginLeft(40)
		        .setFirstLineIndent(70).setMarginTop(20).setTextAlignment(TextAlignment.JUSTIFIED);
		doc.add(parrafo);

		texto = new Text("VISTO el vigente Reglamento del Fondo de Prestaciones Sociales de los " + solicitud.getDesColectivo()
		        + " de 18 de julio de 1991,");
		parrafo = new Paragraph().add(texto).setMargin(0).setPadding(0).setMultipliedLeading(1).setMarginLeft(40)
		        .setFirstLineIndent(70).setMarginTop(20).setTextAlignment(TextAlignment.JUSTIFIED);
		doc.add(parrafo);

		texto = new Text("VISTOS los antecedentes que obran en el Departamento de Acción Social de esta Dirección,");
		parrafo = new Paragraph().add(texto).setMargin(0).setPadding(0).setMultipliedLeading(1).setMarginLeft(40)
		        .setFirstLineIndent(70).setMarginTop(20).setTextAlignment(TextAlignment.JUSTIFIED);
		doc.add(parrafo);

		texto = new Text("PROCEDE denegar la prestación solicitada, por el motivo siguiente:");
		parrafo = new Paragraph().add(texto).setMargin(0).setPadding(0).setMultipliedLeading(1).setMarginLeft(40)
		        .setFirstLineIndent(70).setMarginTop(20).setTextAlignment(TextAlignment.JUSTIFIED);
		doc.add(parrafo);

		texto = new Text(concepto.getDescMotivoDenegacion() + "\"");
		parrafo = new Paragraph().add(texto).setMargin(0).setPadding(0).setMultipliedLeading(1).setMarginLeft(40)
		        .setFirstLineIndent(70).setMarginTop(20).setTextAlignment(TextAlignment.JUSTIFIED);
		doc.add(parrafo);

		texto = new Text("De conformidad con lo establecido en el artículo 32 del Reglamento de Prestaciones Sociales de los "
		        + solicitud.getDesColectivo()
		        + ", contra el presente acuerdo cabe reclamación ante el Letrado Mayor de las Cortes Generales dentro del plazo de un mes, contado a partir del día siguiente al de la recepción de la presente notificación.");
		parrafo = new Paragraph().add(texto).setMargin(0).setPadding(0).setMultipliedLeading(1).setMarginLeft(40)
		        .setFirstLineIndent(70).setMarginTop(20).setTextAlignment(TextAlignment.JUSTIFIED);
		doc.add(parrafo);

		texto = new Text("Lo que pongo en su conocimiento a los efectos oportunos.");
		parrafo = new Paragraph().add(texto).setMargin(0).setPadding(0).setMultipliedLeading(1).setMarginLeft(40)
		        .setFirstLineIndent(70).setMarginTop(20);
		doc.add(parrafo);

		texto = new Text("Palacio del Congreso de los Diputados, a " + sp.format(solicitud.getFechaSolicitud()) + ".");
		parrafo = new Paragraph().add(texto).setMargin(0).setPadding(0).setMultipliedLeading(1).setMarginLeft(40)
		        .setFirstLineIndent(70).setMarginTop(20).setTextAlignment(TextAlignment.JUSTIFIED);
		doc.add(parrafo);

		texto = new Text(firma.getNombreRemite());
		parrafo = new Paragraph().add(texto).setMargin(0).setPadding(0).setMultipliedLeading(1).setMarginLeft(0)
		        .setFirstLineIndent(70).setMarginTop(40).setTextAlignment(TextAlignment.CENTER);
		doc.add(parrafo);

		texto = new Text(firma.getCargoRemite());
		parrafo = new Paragraph().add(texto).setMargin(0).setPadding(0).setMultipliedLeading(1).setMarginLeft(0)
		        .setFirstLineIndent(70).setMarginTop(0).setTextAlignment(TextAlignment.CENTER);
		doc.add(parrafo);

		if (concepto.getEstado() == 90) {
			try {
				final byte[] imagenBytes = solicitudesPrestacionesDao.getImagenFirma(2);
				final ImageData imageData = ImageDataFactory.create(imagenBytes);
				final Image imagen = new Image(imageData);
				imagen.scale(0.2f, 0.2f).setPadding(0).setMarginLeft(200).setMarginTop(10);
				doc.add(imagen);

			} catch (final Exception e) {
				logger.error(e.getMessage());
			}
		}

		final String[] nombres = solicitud.getNombre().split(" ");
		String nombresCap = "";
		for (int i = 0; i < nombres.length; i++) {
			String nombre = nombres[i].toLowerCase();
			nombre = StringUtils.capitalize(nombre);
			nombresCap = nombresCap.concat(nombre + " ");
		}

		final String[] apellidos = solicitud.getApellidos().split(" ");
		String apellidosCap = "";
		for (int i = 0; i < apellidos.length; i++) {
			String apellido = apellidos[i].toLowerCase();
			apellido = StringUtils.capitalize(apellido);
			apellidosCap = apellidosCap.concat(apellido + " ");
		}

		texto = new Text(nombresCap + apellidosCap);
		parrafo = new Paragraph().add(texto).setMargin(0).setPadding(0).setMultipliedLeading(1).setFixedPosition(60, 30, 800);
		doc.add(parrafo);

		doc.close();

		dataStreamForPdf = (new ByteArrayInputStream(outputStream.toByteArray()));

		logger.debug("Sale de generarPDFListado");

		return dataStreamForPdf;

	}


	public static void main(final String[] args) {

		final ConceptoSolBean concepto = new ConceptoSolBean();

		final GenerarInformesServiceImpl generarInformeService = new GenerarInformesServiceImpl();

		try {
			final InputStream initialStream = generarInformeService.generarEscritoDenegacionDirectoraJefa(concepto);

			final byte[] buffer = new byte[initialStream.available()];
			initialStream.read(buffer);

			final File targetFile = new File("C:\\congreso\\Documentacion\\accionSocial\\pruebasInforme\\informe.pdf");
			final OutputStream outStream = new FileOutputStream(targetFile);
			outStream.write(buffer);
			outStream.close();
			System.out.println("Informe generado correctamente");
		} catch (final Exception e) {
			e.printStackTrace();
		}
	}

	private static String html2text(String html) {
		return Jsoup.parse(html).text();
	}
}