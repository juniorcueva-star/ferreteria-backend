package com.ferreteria.service;

import com.ferreteria.dto.comun.PaginaResponse;
import com.ferreteria.dto.cotizaciones.*;
import com.ferreteria.entity.*;
import com.ferreteria.entity.enums.EstadoCotizacion;
import com.ferreteria.exception.*;
import com.ferreteria.repository.*;
import com.ferreteria.security.AccesoUbicacionService;
import com.ferreteria.util.Montos;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.util.*;

@Service @RequiredArgsConstructor
public class CotizacionService {
 private static final BigDecimal CIEN = new BigDecimal("100");
 private final CotizacionRepository cotizacionRepository; private final VentaRepository ventaRepository; private final ClienteRepository clienteRepository;
 private final PresentacionRepository presentacionRepository; private final Buscador buscador; private final AccesoUbicacionService acceso;
 @Transactional public CotizacionResponse crear(CotizacionRequest r) {
   Cotizacion c = nueva(r); return CotizacionResponse.completa(cotizacionRepository.save(c));
 }
 @Transactional public CotizacionResponse actualizar(Long id, CotizacionRequest r) {
   Cotizacion c = bloquear(id); validarBorrador(c); c.setCliente(clienteActivo(r.clienteId())); c.setObservacion(limpiar(r.observacion()));
   c.reemplazarDetalles(detalles(c, r.detalles())); calcular(c); return CotizacionResponse.completa(c);
 }
 @Transactional public CotizacionResponse emitir(Long id) { Cotizacion c=bloquear(id); validarBorrador(c); c.setEstado(EstadoCotizacion.EMITIDA); return CotizacionResponse.completa(c); }
 @Transactional public CotizacionResponse anular(Long id) { Cotizacion c=bloquear(id); if(c.getEstado()==EstadoCotizacion.ANULADA) throw new ReglaNegocioException("La cotizacion ya esta anulada"); c.setEstado(EstadoCotizacion.ANULADA); return CotizacionResponse.completa(c); }
 @Transactional(readOnly=true) public CotizacionResponse obtener(Long id) { Cotizacion c=cotizacionRepository.findById(id).orElseThrow(()->new RecursoNoEncontradoException("Cotizacion",id)); acceso.validarAcceso(c.getUbicacion().getId()); return CotizacionResponse.completa(c, ventaRepository.findByCotizacionId(id).orElse(null)); }
 @Transactional(readOnly=true) public PaginaResponse<CotizacionResponse> listar(Long ubicacionId, EstadoCotizacion estado, Pageable p) {
   Specification<Cotizacion> f=Specification.allOf(Especificaciones.igual("ubicacion.id",acceso.ubicacionParaConsultar(ubicacionId)),Especificaciones.igual("estado",estado));
   var pagina = cotizacionRepository.findAll(f, Ordenamiento.validar(p,"id","fecha","codigo","total"));
   var ids = pagina.getContent().stream().map(Cotizacion::getId).toList();
   Map<Long, Venta> ventas = ids.isEmpty() ? Map.of() : ventaRepository.findByCotizacionIdIn(ids).stream().collect(java.util.stream.Collectors.toMap(v -> v.getCotizacion().getId(), v -> v));
   return PaginaResponse.de(pagina, c -> CotizacionResponse.resumen(c, ventas.get(c.getId())));
 }
 private Cotizacion nueva(CotizacionRequest r) { Long ubicacionId=acceso.ubicacionParaOperar(r.ubicacionId()); Cotizacion c=new Cotizacion(); c.setCodigo("COT-"+UUID.randomUUID().toString().substring(0,8).toUpperCase(Locale.ROOT)); c.setUbicacion(buscador.ubicacionActiva(ubicacionId)); c.setCliente(clienteActivo(r.clienteId())); c.setUsuario(buscador.usuarioActual()); c.setObservacion(limpiar(r.observacion())); c.reemplazarDetalles(detalles(null,r.detalles())); calcular(c); return c; }
 private Cotizacion bloquear(Long id) { Cotizacion c=cotizacionRepository.bloquear(id).orElseThrow(()->new RecursoNoEncontradoException("Cotizacion",id)); acceso.validarAcceso(c.getUbicacion().getId()); return c; }
 private void validarBorrador(Cotizacion c) { if(c.getEstado()!=EstadoCotizacion.BORRADOR) throw new ReglaNegocioException("Solo se puede editar o emitir una cotizacion en BORRADOR"); }
 private Cliente clienteActivo(Long id) { Cliente c=clienteRepository.findById(id).orElseThrow(()->new RecursoNoEncontradoException("Cliente",id)); if(!c.isActivo()) throw new ReglaNegocioException("El cliente "+c.getNombre()+" esta inactivo"); return c; }
 private List<CotizacionDetalle> detalles(Cotizacion previa,List<CotizacionDetalleRequest> rs) { Map<Long,Presentacion> ps=presentacionRepository.findByIdIn(rs.stream().map(CotizacionDetalleRequest::presentacionId).toList()).stream().collect(java.util.stream.Collectors.toMap(Presentacion::getId,x->x)); Map<Long,CotizacionDetalle> anteriores=previa==null?Map.of():previa.getDetalles().stream().collect(java.util.stream.Collectors.toMap(d->d.getPresentacion().getId(),d->d)); Set<Long> vistos=new HashSet<>(); List<CotizacionDetalle> out=new ArrayList<>(); for(CotizacionDetalleRequest r:rs){ if(!vistos.add(r.presentacionId())) throw new ReglaNegocioException("Presentacion repetida en la cotizacion"); Presentacion p=ps.get(r.presentacionId()); if(p==null||!p.isActivo()||!p.getProducto().isActivo()) throw new ReglaNegocioException("La presentacion no esta disponible para cotizar"); CotizacionDetalle a=anteriores.get(p.getId()); CotizacionDetalle d=new CotizacionDetalle(); d.setProducto(p.getProducto());d.setPresentacion(p);d.setDescripcion(p.getProducto().getNombre()+" - "+p.getNombre());d.setCantidad(Montos.cantidad(r.cantidad()));d.setFactor(a==null?p.getFactor():a.getFactor());d.setPrecioUnitario(a==null?p.getPrecioVenta():a.getPrecioUnitario());d.setCantidadBase(Montos.cantidad(d.getCantidad().multiply(d.getFactor()))); aplicarAjuste(d,r); if(d.getCantidadBase().signum()<=0) throw new ReglaNegocioException("Cantidad demasiado pequena");out.add(d);} return out; }
 private void aplicarAjuste(CotizacionDetalle d,CotizacionDetalleRequest r){ if(r.descuento()!=null&&r.ajustePorcentaje()!=null) throw new ReglaNegocioException("Use descuento monetario o ajuste porcentual, no ambos"); BigDecimal imp=Montos.dinero(d.getCantidad().multiply(d.getPrecioUnitario())); BigDecimal p=r.ajustePorcentaje(); if(p!=null&&p.compareTo(CIEN.negate())<0) throw new ReglaNegocioException("El descuento porcentual no puede superar 100 %"); BigDecimal desc=p==null?Montos.dinero(r.descuento()==null?BigDecimal.ZERO:r.descuento()):BigDecimal.ZERO; if(desc.compareTo(imp)>0) throw new ReglaNegocioException("El descuento supera el importe"); BigDecimal sub=p==null?imp.subtract(desc):Montos.dinero(imp.add(imp.multiply(p).divide(CIEN))); if(p!=null&&p.signum()<0) desc=imp.subtract(sub); d.setDescuento(desc);d.setAjustePorcentaje(p);d.setSubtotal(sub); }
 private void calcular(Cotizacion c){ BigDecimal total=c.getDetalles().stream().map(CotizacionDetalle::getSubtotal).reduce(BigDecimal.ZERO,BigDecimal::add);if(total.signum()<=0)throw new ReglaNegocioException("El total de la cotizacion debe ser mayor que 0");BigDecimal sub=Montos.baseSinIgv(total);c.setTotal(Montos.dinero(total));c.setSubtotal(sub);c.setIgv(Montos.dinero(total.subtract(sub)));c.setDescuento(Montos.dinero(c.getDetalles().stream().map(CotizacionDetalle::getDescuento).reduce(BigDecimal.ZERO,BigDecimal::add))); }
 private static String limpiar(String v){return v==null||v.isBlank()?null:v.trim();}
}
