package com.ferreteria.service;

import com.ferreteria.entity.CajaSesion;
import com.ferreteria.entity.Empresa;
import com.ferreteria.entity.MetodoPago;
import com.ferreteria.entity.Pago;
import com.ferreteria.entity.Presentacion;
import com.ferreteria.entity.Producto;
import com.ferreteria.entity.Ubicacion;
import com.ferreteria.entity.Usuario;
import com.ferreteria.entity.enums.Rol;
import com.ferreteria.entity.enums.TipoUbicacion;
import com.ferreteria.entity.enums.UnidadBase;

import java.math.BigDecimal;

/**
 * Objetos de dominio en memoria para las pruebas unitarias (sin base de datos).
 */
final class Fabrica {

    private Fabrica() {
    }

    static Ubicacion tienda(Long id) {
        Empresa empresa = new Empresa();
        empresa.setId(id * 10);
        empresa.setRuc("2000000000" + id);
        empresa.setRazonSocial("Empresa " + id);
        Ubicacion tienda = new Ubicacion();
        tienda.setId(id);
        tienda.setNombre("Tienda " + id);
        tienda.setTipo(TipoUbicacion.TIENDA);
        tienda.setEmpresa(empresa);
        return tienda;
    }

    static Usuario usuario(Long id, Rol rol) {
        Usuario usuario = new Usuario();
        usuario.setId(id);
        usuario.setUsername("usuario" + id);
        usuario.setRol(rol);
        return usuario;
    }

    static CajaSesion caja(Long id, Ubicacion tienda, Usuario usuario, String apertura) {
        CajaSesion caja = new CajaSesion();
        caja.setId(id);
        caja.setUbicacion(tienda);
        caja.setUsuario(usuario);
        caja.setMontoApertura(new BigDecimal(apertura));
        return caja;
    }

    static Presentacion presentacion(Long id, String factor, String precio, UnidadBase unidad) {
        Producto producto = new Producto();
        producto.setId(id * 100);
        producto.setNombre("Producto " + id);
        producto.setUnidadBase(unidad);
        Presentacion presentacion = new Presentacion();
        presentacion.setId(id);
        presentacion.setNombre("Presentacion " + id);
        presentacion.setFactor(new BigDecimal(factor));
        presentacion.setPrecioVenta(new BigDecimal(precio));
        producto.agregarPresentacion(presentacion);
        return presentacion;
    }

    static Pago pago(String metodo, boolean efectivo, String monto, CajaSesion caja, Usuario usuario) {
        MetodoPago metodoPago = new MetodoPago();
        metodoPago.setCodigo(metodo);
        metodoPago.setEfectivo(efectivo);
        Pago pago = new Pago();
        pago.setMetodoPago(metodoPago);
        pago.setMonto(new BigDecimal(monto));
        pago.setCajaSesion(caja);
        pago.setUsuario(usuario);
        return pago;
    }
}
