package com.acrqg.platform.scanner.source;

/** Prepares checked-out source code for static scanners. */
public interface SourceMaterializationService {

    MaterializedSource materialize(SourceMaterializationRequest request);
}
