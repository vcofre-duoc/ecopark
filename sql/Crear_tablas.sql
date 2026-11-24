CREATE TABLE IF NOT EXISTS estadias (
    id BIGINT NOT NULL AUTO_INCREMENT,
    patente VARCHAR(12) NOT NULL,
    tipo VARCHAR(10) NOT NULL,
    horas INT NOT NULL,
    convenio BOOLEAN NOT NULL DEFAULT FALSE,
    PRIMARY KEY (id), 
    CONSTRAINT chk_ecopark_tipo CHECK (tipo IN ('Auto', 'Moto')),
    CONSTRAINT chk_ecopark_horas CHECK (horas BETWEEN 1 AND 24)
);


